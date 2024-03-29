/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.pushpullnotificationsapi.scheduled

import java.time.{Clock, Duration, Instant}
import javax.inject.Inject
import scala.concurrent.Future.successful
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import com.google.inject.Singleton
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}
import uk.gov.hmrc.thirdpartydelegatedauthority.util.FutureUtils

import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.FAILED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, RetryableNotification}
import uk.gov.hmrc.pushpullnotificationsapi.repository.NotificationsRepository
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationPushService

@Singleton
class RetryPushNotificationsJob @Inject() (
    mongoLockRepository: MongoLockRepository,
    jobConfig: RetryPushNotificationsJobConfig,
    notificationsRepository: NotificationsRepository,
    notificationPushService: NotificationPushService,
    val clock: Clock
  )(implicit mat: Materializer)
    extends ScheduledMongoJob with ClockNow {

  override def name: String = "RetryPushNotificationsJob"
  override def interval: FiniteDuration = jobConfig.interval
  override def initialDelay: FiniteDuration = jobConfig.initialDelay
  override val isEnabled: Boolean = jobConfig.enabled
  implicit val hc: HeaderCarrier = HeaderCarrier()
  lazy override val lockKeeper: LockService = LockService(mongoLockRepository, lockId = "RetryPushNotificationsJob", ttl = 1.hour)

  override def runJob(implicit ec: ExecutionContext): Future[RunningOfJobSuccessful] = {
    val retryAfterDateTime: Instant = instant()
    val nextRetryAfterDateTime: Instant = retryAfterDateTime.plus(Duration.ofMillis(jobConfig.interval.toMillis))

    FutureUtils.timeThisFuture(
      {
        notificationPushService
          .fetchRetryablePushNotifications(retryAfterDateTime)
          .flatMap(source =>
            source.runWith(Sink.foreachAsync[RetryableNotification](jobConfig.parallelism)(retryPushNotification(_, nextRetryAfterDateTime)))
              .map(_ => RunningOfJobSuccessful)
          )
          .recoverWith {
            case NonFatal(e) =>
              logger.error("Failed to retry failed push pull notifications", e)
              Future.failed(RunningOfJobFailed(name, e))
          }
      },
      "FetchRetryableNotifications"
    )
  }

  private def retryPushNotification(retryableNotification: RetryableNotification, retryAfterDateTime: Instant)(implicit ec: ExecutionContext): Future[Unit] = {
    notificationPushService
      .handlePushNotification(retryableNotification.box, retryableNotification.notification)
      .flatMap(success => if (success) successful(()) else updateFailedNotification(retryableNotification.notification, retryAfterDateTime))
      .recover {
        case NonFatal(e) =>
          logger.error(s"Unexpected error retrying notification ${retryableNotification.notification.notificationId} with exception: $e")
          successful(())
      }
  }

  private def updateFailedNotification(notification: Notification, retryAfterDateTime: Instant)(implicit ec: ExecutionContext): Future[Unit] = {
    if (notification.createdDateTime.isAfter(instant().minus(Duration.ofHours(jobConfig.numberOfHoursToRetry)))) {
      notificationsRepository.updateRetryAfterDateTime(notification.notificationId, retryAfterDateTime).map(_ => ())
    } else {
      notificationsRepository.updateStatus(notification.notificationId, FAILED).map(_ => ())
    }
  }
}

case class RetryPushNotificationsJobConfig(initialDelay: FiniteDuration, interval: FiniteDuration, enabled: Boolean, numberOfHoursToRetry: Int, parallelism: Int)

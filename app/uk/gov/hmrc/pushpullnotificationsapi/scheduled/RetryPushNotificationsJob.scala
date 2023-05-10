/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time.{Duration, Instant}
import javax.inject.Inject
import scala.concurrent.Future.successful
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.google.inject.Singleton

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}
import uk.gov.hmrc.thirdpartydelegatedauthority.utils.FutureUtils

import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.FAILED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, RetryableNotification}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{BoxRepository, NotificationsRepository}
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationPushService

@Singleton
class RetryPushNotificationsJob @Inject() (
    mongoLockRepository: MongoLockRepository,
    jobConfig: RetryPushNotificationsJobConfig,
    boxRepository: BoxRepository,
    notificationsRepository: NotificationsRepository,
    notificationPushService: NotificationPushService
  )(implicit mat: Materializer)
    extends ScheduledMongoJob {

  override def name: String = "RetryPushNotificationsJob"
  override def interval: FiniteDuration = jobConfig.interval
  override def initialDelay: FiniteDuration = jobConfig.initialDelay
  override val isEnabled: Boolean = jobConfig.enabled
  implicit val hc: HeaderCarrier = HeaderCarrier()
  lazy override val lockKeeper: LockService = LockService(mongoLockRepository, lockId = "RetryPushNotificationsJob", ttl = 1.hour)

  override def runJob(implicit ec: ExecutionContext): Future[RunningOfJobSuccessful] = {
    val retryAfterDateTime: Instant = Instant.now.plus(Duration.ofMillis(jobConfig.interval.toMillis))

    FutureUtils.timeThisFuture(
      {
        boxRepository
          .fetchRetryableNotifications
          .runWith(Sink.foreachAsync[RetryableNotification](jobConfig.parallelism)(retryPushNotification(_, retryAfterDateTime)))
          .map(_ => RunningOfJobSuccessful)
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
          throw e
      }
  }

  private def updateFailedNotification(notification: Notification, retryAfterDateTime: Instant)(implicit ec: ExecutionContext): Future[Unit] = {
    if (notification.createdDateTime.isAfter(Instant.now.minus(Duration.ofHours(jobConfig.numberOfHoursToRetry)))) {
      notificationsRepository.updateRetryAfterDateTime(notification.notificationId, retryAfterDateTime).map(_ => ())
    } else {
      notificationsRepository.updateStatus(notification.notificationId, FAILED).map(_ => ())
    }
  }
}

case class RetryPushNotificationsJobConfig(initialDelay: FiniteDuration, interval: FiniteDuration, enabled: Boolean, numberOfHoursToRetry: Int, parallelism: Int)

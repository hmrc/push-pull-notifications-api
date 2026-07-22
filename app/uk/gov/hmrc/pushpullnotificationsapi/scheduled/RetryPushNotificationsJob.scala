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
import javax.inject.{Inject, Named}
import scala.concurrent.Future.successful
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import com.google.inject.Singleton
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.thirdpartydelegatedauthority.util.FutureUtils

import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.FAILED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, RetryableNotification}
import uk.gov.hmrc.pushpullnotificationsapi.repository.NotificationsRepository
import uk.gov.hmrc.pushpullnotificationsapi.scheduling.*
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationPushService
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

@Singleton
class RetryPushNotificationsJob @Inject() (
    val mongoLockRepository: MongoLockRepository,
    @Named("RetryPushNotificationsJob") jobConfig: ScheduledJobConfig,
    notificationsRepository: NotificationsRepository,
    notificationPushService: NotificationPushService,
    val clock: Clock
  )(implicit mat: Materializer)
    extends LockedScheduledJob with ClockNow with ApplicationLogger {

  val initialDelay: FiniteDuration = jobConfig.initialDelay

  val interval: FiniteDuration = jobConfig.interval

  val isEnabled: Boolean = jobConfig.isEnabled

  val parallelism: Int = jobConfig.parallelism

  val numberOfHoursToRetry: Int = jobConfig.numberOfHoursToRetry

  val name: String = "RetryPushNotificationsJob"

  given HeaderCarrier = HeaderCarrier()

  override def executeInLock(using ExecutionContext): Future[String] = {
    val retryAfterDateTime: Instant = instant
    val nextRetryAfterDateTime: Instant = retryAfterDateTime.plus(Duration.ofMillis(jobConfig.interval.toMillis))

    FutureUtils.timeThisFuture(
      {
        notificationPushService
          .fetchRetryablePushNotifications(retryAfterDateTime)
          .flatMap(source =>
            source.runWith(Sink.foreachAsync[RetryableNotification](parallelism)(retryPushNotification(_, nextRetryAfterDateTime)))
              .map(_ => "Successful")
          )
          .recoverWith {
            case NonFatal(e) =>
              logger.error("Failed to retry failed push pull notifications", e)
              Future.failed(e)
          }
      },
      "FetchRetryableNotifications"
    )
  }

  private def retryPushNotification(retryableNotification: RetryableNotification, retryAfterDateTime: Instant)(using ExecutionContext): Future[Unit] = {
    notificationPushService
      .handlePushNotification(retryableNotification.box, retryableNotification.notification)
      .flatMap(success => if (success) successful(()) else updateFailedNotification(retryableNotification.notification, retryAfterDateTime))
      .recover {
        case NonFatal(e) =>
          logger.error(s"Unexpected error retrying notification ${retryableNotification.notification.notificationId} with exception: $e")
          successful(())
      }
  }

  private def updateFailedNotification(notification: Notification, retryAfterDateTime: Instant)(using ExecutionContext): Future[Unit] = {
    if (notification.createdDateTime.isAfter(instant.minus(Duration.ofHours(numberOfHoursToRetry)))) {
      notificationsRepository.updateRetryAfterDateTime(notification.notificationId, retryAfterDateTime).map(_ => ())
    } else {
      notificationsRepository.updateStatus(notification.notificationId, FAILED).map(_ => ())
    }
  }
}

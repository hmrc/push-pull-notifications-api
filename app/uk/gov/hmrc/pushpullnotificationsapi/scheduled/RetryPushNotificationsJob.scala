/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.google.inject.Singleton
import javax.inject.Inject
import org.joda.time.DateTime.now
import org.joda.time.DateTimeZone.UTC
import org.joda.time.{DateTime, Duration}
import play.api.Logger
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.FAILED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, RetryableNotification}
import uk.gov.hmrc.pushpullnotificationsapi.repository.NotificationsRepository
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationPushService

import scala.concurrent.Future.successful
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class RetryPushNotificationsJob @Inject()(override val lockKeeper: RetryPushNotificationsJobLockKeeper,
                                          jobConfig: RetryPushNotificationsJobConfig,
                                          notificationsRepository: NotificationsRepository,
                                          notificationPushService: NotificationPushService)
                                         (implicit mat: Materializer) extends ScheduledMongoJob {

  override def name: String = "RetryPushNotificationsJob"
  override def interval: FiniteDuration = jobConfig.interval
  override def initialDelay: FiniteDuration = jobConfig.initialDelay
  override val isEnabled: Boolean = jobConfig.enabled
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def runJob(implicit ec: ExecutionContext): Future[RunningOfJobSuccessful] = {
    val retryAfterDateTime: DateTime = now(UTC).plus(jobConfig.interval.toMillis)

    notificationsRepository
      .fetchRetryableNotifications
      .runWith(Sink.foreachAsync[RetryableNotification](jobConfig.parallelism)(retryPushNotification(_, retryAfterDateTime)))
      .map(_ => RunningOfJobSuccessful)
      .recoverWith {
        case NonFatal(e) =>
          Logger.error("Failed to retry failed push pull notifications", e)
          Future.failed(RunningOfJobFailed(name, e))
    }
  }

  private def retryPushNotification(retryableNotification: RetryableNotification, retryAfterDateTime: DateTime)(implicit ec: ExecutionContext): Future[Unit] = {
    notificationPushService
      .handlePushNotification(retryableNotification.box, retryableNotification.notification)
      .flatMap(success => if (success) successful(()) else updateFailedNotification(retryableNotification.notification, retryAfterDateTime))
      .recover {
        case NonFatal(e) =>
          Logger.error(s"Unexpected error retrying notification ${retryableNotification.notification.notificationId} with exception: $e")
          throw e
      }
  }

  private def updateFailedNotification(notification: Notification, retryAfterDateTime: DateTime)(implicit ec: ExecutionContext): Future[Unit] = {
    if (notification.createdDateTime.isAfter(now(UTC).minusHours(jobConfig.numberOfHoursToRetry))) {
      notificationsRepository.updateRetryAfterDateTime(notification.notificationId, retryAfterDateTime).map(_ => ())
    } else {
      notificationsRepository.updateStatus(notification.notificationId, FAILED).map(_ => ())
    }
  }
}

class RetryPushNotificationsJobLockKeeper @Inject()(mongo: ReactiveMongoComponent) extends LockKeeper {
  override def repo: LockRepository = new LockRepository()(mongo.mongoConnector.db)

  override def lockId: String = "RetryPushNotificationsJob"

  override val forceLockReleaseAfter: Duration = Duration.standardMinutes(60) // scalastyle:off magic.number
}

case class RetryPushNotificationsJobConfig(initialDelay: FiniteDuration,
                                           interval: FiniteDuration,
                                           enabled: Boolean,
                                           numberOfHoursToRetry: Int,
                                           parallelism: Int)

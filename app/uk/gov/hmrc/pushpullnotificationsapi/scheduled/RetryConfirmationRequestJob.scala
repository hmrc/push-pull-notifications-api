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

import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.ConfirmationStatus
import uk.gov.hmrc.pushpullnotificationsapi.repository.ConfirmationRepository
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.ConfirmationRequest
import uk.gov.hmrc.pushpullnotificationsapi.services.ConfirmationService

@Singleton
class RetryConfirmationRequestJob @Inject() (
    mongoLockRepository: MongoLockRepository,
    jobConfig: RetryConfirmationRequestJobConfig,
    repo: ConfirmationRepository,
    service: ConfirmationService
  )(implicit mat: Materializer)
    extends ScheduledMongoJob {

  override def name: String = "RetryConfirmationRequestJob"
  override def interval: FiniteDuration = jobConfig.interval
  override def initialDelay: FiniteDuration = jobConfig.initialDelay
  override val isEnabled: Boolean = jobConfig.enabled
  implicit val hc: HeaderCarrier = HeaderCarrier()
  lazy override val lockKeeper: LockService = LockService(mongoLockRepository, lockId = "RetryConfirmationRequestJob", ttl = 1.hour)

  override def runJob(implicit ec: ExecutionContext): Future[RunningOfJobSuccessful] = {
    val retryAfterDateTime: Instant = Instant.now.plus(Duration.ofMillis(jobConfig.interval.toMillis))

    repo
      .fetchRetryableConfirmations
      .runWith(Sink.foreachAsync[ConfirmationRequest](jobConfig.parallelism)(retryConfirmation(_, retryAfterDateTime)))
      .map(_ => RunningOfJobSuccessful)
      .recoverWith {
        case NonFatal(e) =>
          logger.error("Failed to retry failed push pull confirmation", e)
          Future.failed(RunningOfJobFailed(name, e))
      }
  }

  private def retryConfirmation(confirmation: ConfirmationRequest, retryAfterDateTime: Instant)(implicit ec: ExecutionContext): Future[Unit] = {
    service
      .sendConfirmation(confirmation)
      .flatMap(success => if (success) successful(()) else updateFailedNotification(confirmation, retryAfterDateTime))
      .recover {
        case NonFatal(e) =>
          logger.error(s"Unexpected error retrying confirmation ${confirmation.confirmationId} with exception: $e")
          successful(())
      }
  }

  private def updateFailedNotification(confirmation: ConfirmationRequest, retryAfterDateTime: Instant)(implicit ec: ExecutionContext): Future[Unit] = {
    if (confirmation.createdDateTime.isAfter(Instant.now.minus(Duration.ofHours(jobConfig.numberOfHoursToRetry)))) {
      repo.updateRetryAfterDateTime(confirmation.notificationId, retryAfterDateTime).map(_ => ())
    } else {
      repo.updateStatus(confirmation.notificationId, ConfirmationStatus.FAILED).map(_ => ())
    }
  }
}

case class RetryConfirmationRequestJobConfig(initialDelay: FiniteDuration, interval: FiniteDuration, enabled: Boolean, numberOfHoursToRetry: Int, parallelism: Int)

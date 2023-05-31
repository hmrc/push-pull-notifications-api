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

/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.pushpullnotificationsapi.scheduled

import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit.{HOURS, SECONDS}
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.duration.FiniteDuration

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.MongoLockRepository

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ConfirmationStatus, _}
import uk.gov.hmrc.pushpullnotificationsapi.repository.ConfirmationRepository
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.ConfirmationRequest
import uk.gov.hmrc.pushpullnotificationsapi.services.ConfirmationService

class RetryConfirmationRequestJobSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite {

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .disable[com.kenshoo.play.metrics.PlayModule]
    .configure("metrics.enabled" -> false).build()
  implicit lazy val materializer: Materializer = app.materializer

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val mongoLockRepo: MongoLockRepository = mock[MongoLockRepository]

    val jobConfig: RetryConfirmationRequestJobConfig = RetryConfirmationRequestJobConfig(
      FiniteDuration(60, SECONDS),
      FiniteDuration(24, HOURS),
      enabled = true,
      6,
      1
    )
    val mockRepo: ConfirmationRepository = mock[ConfirmationRepository]
    val mockService: ConfirmationService = mock[ConfirmationService]

    val underTest = new RetryConfirmationRequestJob(
      mongoLockRepo,
      jobConfig,
      mockRepo,
      mockService
    )
    when(mongoLockRepo.isLocked(*, *)).thenReturn(successful(true))
    when(mongoLockRepo.takeLock(*, *, *)).thenReturn(successful(true))
    when(mongoLockRepo.releaseLock(*, *)).thenReturn(successful(()))
  }

  "RetryConfirmationRequestJob" should {
    import scala.concurrent.ExecutionContext.Implicits.global

    val notificationId = NotificationId.random
    "retry pushing the notifications" in new Setup {
      val request: ConfirmationRequest = ConfirmationRequest(confirmationId = ConfirmationId.random, "URL", notificationId = notificationId)
      when(mockRepo.fetchRetryableConfirmations).thenReturn(Source.future(successful(request)))
      when(mockService.sendConfirmation(*)(*, *)).thenReturn(successful(true))

      val result: underTest.Result = await(underTest.execute)

      verify(mockService).sendConfirmation(eqTo(request))(*, *)
      result.message shouldBe "RetryConfirmationRequestJob Job ran successfully."
    }

    "set notification RetryAfterDateTime when it fails to push and the notification is not too old for further retries" in new Setup {

      val request: ConfirmationRequest = ConfirmationRequest(confirmationId = ConfirmationId.random, "URL", notificationId = notificationId)
      when(mockRepo.fetchRetryableConfirmations)
        .thenReturn(Source.future(successful(request)))
      when(mockRepo.updateRetryAfterDateTime(NotificationId(*), *)).thenReturn(successful(Some(request)))
      when(mockService.sendConfirmation(*)(*, *)).thenReturn(successful(false))

      val result: underTest.Result = await(underTest.execute)

      verify(mockRepo).updateRetryAfterDateTime(NotificationId(*), *)
      result.message shouldBe "RetryConfirmationRequestJob Job ran successfully."
    }

    "set notification status to failed when it fails to push and the notification is too old for further retries" in new Setup {

      val request: ConfirmationRequest =
        ConfirmationRequest(confirmationId = ConfirmationId.random, "URL", notificationId = notificationId, createdDateTime = Instant.now.minus(Duration.ofHours(7)))
      when(mockRepo.fetchRetryableConfirmations)
        .thenReturn(Source.future(successful(request)))
      when(mockRepo.updateStatus(notificationId, ConfirmationStatus.FAILED)).thenReturn(successful(Some(request)))
      when(mockService.sendConfirmation(*)(*, *)).thenReturn(successful(false))

      val result: underTest.Result = await(underTest.execute)

      verify(mockRepo, times(1)).updateStatus(notificationId, ConfirmationStatus.FAILED)
      verify(mockRepo, never).updateRetryAfterDateTime(NotificationId(*), *)
      result.message shouldBe "RetryConfirmationRequestJob Job ran successfully."
    }

    "not execute if the job is already running" in new Setup {
      when(mockService.sendConfirmation(*)(*, *)).thenReturn(successful(true))
      val request: ConfirmationRequest = ConfirmationRequest(confirmationId = ConfirmationId.random, "URL", notificationId = notificationId)
      when(mockRepo.fetchRetryableConfirmations)
        .thenReturn(Source.future(successful(request)))
      when(mongoLockRepo.isLocked(*, *)).thenReturn(successful(true)).andThenAnswer(successful(true)).andThenAnswer(successful(false))
      when(mongoLockRepo.takeLock(*, *, *)).thenReturn(successful(true)).andThenAnswer(successful(false))
      when(mongoLockRepo.releaseLock(*, *)).thenReturn(successful(()))

      val _: underTest.Result = await(underTest.execute)
      val result2: underTest.Result = await(underTest.execute)

      verify(mockRepo, times(1)).fetchRetryableConfirmations
      verify(mockService, times(1)).sendConfirmation(*)(*, *)
      result2.message shouldBe "RetryConfirmationRequestJob did not run because repository was locked by another instance of the scheduler."
    }

    "handle error when something fails" in new Setup {
      when(mockRepo.fetchRetryableConfirmations)
        .thenReturn(Source.future(failed(new RuntimeException("Failed"))))

      val result: underTest.Result = await(underTest.execute)

      verify(mockService, never).sendConfirmation(*)(*, *)
      result.message shouldBe "The execution of scheduled job RetryConfirmationRequestJob failed with error 'Failed'. " +
        "The next execution of the job will do retry."
    }
  }
}

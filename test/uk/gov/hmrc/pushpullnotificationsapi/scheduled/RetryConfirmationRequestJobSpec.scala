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

import java.util.concurrent.TimeUnit.{HOURS, SECONDS}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko.stream.Materializer
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.mocks.ConfirmationServiceMockModule
import uk.gov.hmrc.pushpullnotificationsapi.mocks.repository.{ConfirmationRepositoryMockModule, MongoLockRepositoryMockModule}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.ConfirmationStatus.FAILED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications._
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.ConfirmationRequest
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

class RetryConfirmationRequestJobSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite {

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .configure("metrics.enabled" -> false).build()
  implicit lazy val materializer: Materializer = app.materializer

  class Setup(val batch: Int = 5) extends MongoLockRepositoryMockModule with ConfirmationServiceMockModule with ConfirmationRepositoryMockModule with TestData {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val jobConfig: RetryConfirmationRequestJobConfig = RetryConfirmationRequestJobConfig(
      FiniteDuration(60, SECONDS),
      FiniteDuration(24, HOURS),
      enabled = true,
      numberOfHoursToRetry = 6,
      parallelism = batch
    )

    val underTest = new RetryConfirmationRequestJob(
      MongoLockRepositoryMock.aMock,
      jobConfig,
      ConfirmationRepositoryMock.aMock,
      ConfirmationServiceMock.aMock,
      FixedClock.clock
    )
    MongoLockRepositoryMock.IsLocked.theSuccess(true)
    MongoLockRepositoryMock.TakeLock.thenSuccess(true)
    MongoLockRepositoryMock.ReleaseLock.thenSuccess()

    def setUpSuccessMocksForRequest(confirmationRequest: ConfirmationRequest) = {
      ConfirmationServiceMock.SendConfirmation.thenSuccessWithDelayFor(confirmationRequest)
      ConfirmationRepositoryMock.UpdateRetryAfterDateTime.thenSuccessWith(Some(confirmationRequest))
    }

    def setupFailureSendConfirmationMock(confirmationRequest: ConfirmationRequest) = {
      ConfirmationServiceMock.SendConfirmation.thenfailsFor(confirmationRequest)
    }

    def buildSuccess(i: Int): List[ConfirmationRequest] = {
      Range.inclusive(1, i).map(_ => {
        val notificationId = NotificationId.random
        val confirmationId = ConfirmationId.random
        val request = confirmationRequest.copy(confirmationId = confirmationId, notificationId = notificationId)
        setUpSuccessMocksForRequest(request)
        request
      })
        .toList
    }

    def buildFailed(i: Int): List[ConfirmationRequest] = {
      Range.inclusive(1, i).map(_ => {
        val notificationId = NotificationId.random
        val confirmationId = ConfirmationId.random
        val request = confirmationRequest.copy(confirmationId = confirmationId, notificationId = notificationId)
        setupFailureSendConfirmationMock(request)
        request
      })
        .toList
    }

    def runBatchTest(numberBad: Int, numberGood: Int)(implicit ec: ExecutionContext) = {
      val bad = buildFailed(numberBad)
      val good = buildSuccess(numberGood)

      ConfirmationRepositoryMock.FetchRetryableConfirmations.thenSuccessWith(bad ++ good)

      val result: underTest.Result = await(underTest.execute)

      bad.foreach(x => ConfirmationServiceMock.SendConfirmation.verifyCalledWith(x))
      good.foreach(x => ConfirmationServiceMock.SendConfirmation.verifyCalledWith(x))

      result
    }
  }

  "RetryConfirmationRequestJob" should {
    import scala.concurrent.ExecutionContext.Implicits.global
    "retry pushing the notifications" in new Setup {

      ConfirmationRepositoryMock.FetchRetryableConfirmations.thenSuccessWith(List(confirmationRequest))
      ConfirmationServiceMock.SendConfirmation.thenSuccess(true)
      val result: underTest.Result = await(underTest.execute)

      ConfirmationServiceMock.SendConfirmation.verifyCalledWith(confirmationRequest)
      result.message shouldBe "RetryConfirmationRequestJob Job ran successfully."
    }

    "set notification RetryAfterDateTime when it fails to push and the notification is not too old for further retries" in new Setup {

      ConfirmationRepositoryMock.FetchRetryableConfirmations.thenSuccessWith(List(confirmationRequest))
      ConfirmationRepositoryMock.UpdateRetryAfterDateTime.thenSuccessWith(Some(confirmationRequest))
      ConfirmationServiceMock.SendConfirmation.thenSuccess(false)

      val result: underTest.Result = await(underTest.execute)

      ConfirmationRepositoryMock.UpdateRetryAfterDateTime.verifyCalled()
      result.message shouldBe "RetryConfirmationRequestJob Job ran successfully."
    }

    "set confirmation status to failed when it fails to push and the confirmation is too old for further retries" in new Setup {

      ConfirmationRepositoryMock.FetchRetryableConfirmations.thenSuccessWith(List(outOfDateConfirmationRequest))
      ConfirmationRepositoryMock.UpdateStatus.isSuccessWith(notificationId, FAILED, outOfDateConfirmationRequest)

      ConfirmationServiceMock.SendConfirmation.thenSuccess(false)

      val result: underTest.Result = await(underTest.execute)

      ConfirmationRepositoryMock.FetchRetryableConfirmations.verifyCalledOnce()
      ConfirmationRepositoryMock.UpdateStatus.verifyCalledWith(notificationId, FAILED)
      ConfirmationRepositoryMock.UpdateRetryAfterDateTime.neverCalled()

      result.message shouldBe "RetryConfirmationRequestJob Job ran successfully."
    }

    "not execute if the job is already running" in new Setup {

      ConfirmationServiceMock.SendConfirmation.thenSuccess(true)
      ConfirmationRepositoryMock.FetchRetryableConfirmations.thenSuccessWith(List(confirmationRequest))
      MongoLockRepositoryMock.IsLocked.thenTrueTrueFalse()
      MongoLockRepositoryMock.TakeLock.thenTrueFalse()
      MongoLockRepositoryMock.ReleaseLock.thenSuccess()

      await(underTest.execute)
      val result2: underTest.Result = await(underTest.execute)

      ConfirmationRepositoryMock.FetchRetryableConfirmations.verifyCalledOnce()
      ConfirmationServiceMock.SendConfirmation.verifyCalled()
      result2.message shouldBe "RetryConfirmationRequestJob did not run because repository was locked by another instance of the scheduler."
    }

    "handle error when something fails" in new Setup {

      ConfirmationRepositoryMock.FetchRetryableConfirmations.thenFails()
      val result: underTest.Result = await(underTest.execute)

      ConfirmationServiceMock.SendConfirmation.neverCalled()
      result.message shouldBe "The execution of scheduled job RetryConfirmationRequestJob failed with error 'Failed'. " +
        "The next execution of the job will do retry."
    }

    "attempt to send all even if 1st fails with one batch worth of requests" in new Setup(5) {
      runBatchTest(numberBad = 2, numberGood = 3).message shouldBe "RetryConfirmationRequestJob Job ran successfully."
    }

    "attempt to send all even if 1st fails with more than one batch worth of requests" in new Setup(5) {
      runBatchTest(numberBad = 1, numberGood = 15).message shouldBe "RetryConfirmationRequestJob Job ran successfully."
    }
  }
}

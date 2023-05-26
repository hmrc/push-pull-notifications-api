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
import java.util.UUID
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
import uk.gov.hmrc.pushpullnotificationsapi.mocks.repository.{MongoLockRepositoryMockModule, NotificationsRepositoryMockModule}
import uk.gov.hmrc.pushpullnotificationsapi.mocks._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.FAILED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications._
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationPushService
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

import scala.concurrent.ExecutionContext.Implicits.global

class RetryPushNotificationsJobSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite {

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .disable[com.kenshoo.play.metrics.PlayModule]
    .configure("metrics.enabled" -> false).build()
  implicit lazy val materializer: Materializer = app.materializer

  trait Setup extends NotificationsRepositoryMockModule
  with NotificationPushServiceMockModule
  with MongoLockRepositoryMockModule
  with TestData {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val mongoLockRepo: MongoLockRepository = mock[MongoLockRepository]

    val retryPushNotificationsJobConfig: RetryPushNotificationsJobConfig = RetryPushNotificationsJobConfig(
      FiniteDuration(60, SECONDS),
      FiniteDuration(24, HOURS),
      enabled = true,
      6,
      1
    )

    val underTest = new RetryPushNotificationsJob(
      MongoLockRepositoryMock.aMock,
      retryPushNotificationsJobConfig,
      NotificationsRepositoryMock.aMock,
      NotificationPushServiceMock.aMock
    )

    MongoLockRepositoryMock.IsLocked.theSuccess(true)
    MongoLockRepositoryMock.TakeLock.thenSuccess(true)
    MongoLockRepositoryMock.ReleaseLock.thenSuccess
  }

  "RetryPushNotificationsJob" should {
    import scala.concurrent.ExecutionContext.Implicits.global


    "retry pushing the notifications" in new Setup {
      val notification: Notification =
        Notification(NotificationId(UUID.randomUUID()), BoxId(UUID.randomUUID()), MessageContentType.APPLICATION_JSON, "{}", NotificationStatus.FAILED)
      val retryableNotification: RetryableNotification = RetryableNotification(notification, box)
      NotificationPushServiceMock.FetchRetryablePushNotifications.succeedsFor(retryableNotification)
      NotificationPushServiceMock.HandlePushNotification.returnsTrue()

      val result: underTest.Result = await(underTest.execute)

      NotificationPushServiceMock.HandlePushNotification.verifyCalledWith(box, notification)
      result.message shouldBe "RetryPushNotificationsJob Job ran successfully."
    }

    "set notification RetryAfterDateTime when it fails to push and the notification is not too old for further retries" in new Setup {
      val notification: Notification = Notification(
        NotificationId(UUID.randomUUID()),
        BoxId(UUID.randomUUID()),
        MessageContentType.APPLICATION_JSON,
        "{}",
        NotificationStatus.FAILED,
        Instant.now.minus(Duration.ofHours(5))
      )
      val retryableNotification: RetryableNotification = RetryableNotification(notification, box)
      NotificationPushServiceMock.FetchRetryablePushNotifications.succeedsFor(retryableNotification)
      NotificationsRepositoryMock.UpdateRetryAfterDateTime.returnsSuccessWith(notification)
      NotificationPushServiceMock.HandlePushNotification.returnsFalse()

      val result: underTest.Result = await(underTest.execute)

      NotificationsRepositoryMock.UpdateRetryAfterDateTime.verifyCalled()
      result.message shouldBe "RetryPushNotificationsJob Job ran successfully."
    }

    "set notification status to failed when it fails to push and the notification is too old for further retries" in new Setup {
      val notification: Notification = Notification(
        NotificationId(UUID.randomUUID()),
        BoxId(UUID.randomUUID()),
        MessageContentType.APPLICATION_JSON,
        "{}",
        NotificationStatus.FAILED,
        Instant.now.minus(Duration.ofHours(7))
      )
      val retryableNotification: RetryableNotification = RetryableNotification(notification, box)
      NotificationPushServiceMock.FetchRetryablePushNotifications.succeedsFor(retryableNotification)
      NotificationsRepositoryMock.UpdateStatus.succeedsFor(notification, FAILED)

       NotificationPushServiceMock.HandlePushNotification.returnsFalse()

      val result: underTest.Result = await(underTest.execute)

      NotificationsRepositoryMock.UpdateStatus.verifyCalled()
      NotificationsRepositoryMock.UpdateRetryAfterDateTime.verifyNeverCalled()
      result.message shouldBe "RetryPushNotificationsJob Job ran successfully."
    }

    "not execute if the job is already running" in new Setup {
      val notification: Notification = Notification(
        NotificationId(UUID.randomUUID()),
        BoxId(UUID.randomUUID()),
        MessageContentType.APPLICATION_JSON,
        "{}",
        NotificationStatus.FAILED,
        Instant.now.minus(Duration.ofHours(7))
      )
      val retryableNotification: RetryableNotification = RetryableNotification(notification, box)
      NotificationPushServiceMock.HandlePushNotification.returnsTrue()
      NotificationPushServiceMock.FetchRetryablePushNotifications.succeedsFor(retryableNotification)

      MongoLockRepositoryMock.IsLocked.thenTrueTrueFalse()
      MongoLockRepositoryMock.TakeLock.thenTrueFalse()
      MongoLockRepositoryMock.ReleaseLock.thenSuccess()

      val _ = await(underTest.execute)
      val result2: underTest.Result = await(underTest.execute)

      NotificationPushServiceMock.FetchRetryablePushNotifications.verifyCalled()
      NotificationPushServiceMock.HandlePushNotification.verifyCalled()
      result2.message shouldBe "RetryPushNotificationsJob did not run because repository was locked by another instance of the scheduler."
    }

    "handle error when something fails" in new Setup {
      NotificationPushServiceMock.FetchRetryablePushNotifications.failsWithException()

      val result: underTest.Result = await(underTest.execute)

      NotificationPushServiceMock.HandlePushNotification.verifyNeverCalled()
      result.message shouldBe "The execution of scheduled job RetryPushNotificationsJob failed with error 'Failed'. " +
        "The next execution of the job will do retry."
    }
  }
}

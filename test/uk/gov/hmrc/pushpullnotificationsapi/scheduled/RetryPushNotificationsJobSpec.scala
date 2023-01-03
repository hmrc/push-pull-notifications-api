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


import java.util.UUID
import java.util.concurrent.TimeUnit.{HOURS, SECONDS}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.joda.time.DateTime.now
import org.joda.time.DateTimeZone.UTC
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.FAILED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications._
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, BoxCreator, BoxId, ClientId, PushSubscriber}
import uk.gov.hmrc.pushpullnotificationsapi.repository.NotificationsRepository
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationPushService
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.scheduling.ExclusiveScheduledJob

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.duration.FiniteDuration

class RetryPushNotificationsJobSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite {

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .disable[com.kenshoo.play.metrics.PlayModule]
    .configure("metrics.enabled" -> false).build()
  implicit lazy val materializer: Materializer = app.materializer

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val mongoLockRepo: MongoLockRepository = mock[MongoLockRepository]
    val retryPushNotificationsJobConfig: RetryPushNotificationsJobConfig = RetryPushNotificationsJobConfig(
      FiniteDuration(60, SECONDS), FiniteDuration(24, HOURS), enabled = true, 6, 1)
    val mockNotificationsRepository: NotificationsRepository = mock[NotificationsRepository]
    val mockNotificationPushService: NotificationPushService = mock[NotificationPushService]
    val underTest = new RetryPushNotificationsJob(
      mongoLockRepo,
      retryPushNotificationsJobConfig,
      mockNotificationsRepository,
      mockNotificationPushService
    )
    when(mongoLockRepo.isLocked(*, *)).thenReturn(successful(true))
    when(mongoLockRepo.takeLock(*, *, *)).thenReturn(successful(true))
    when(mongoLockRepo.releaseLock(*, *)).thenReturn(successful(true))
  }

  "RetryPushNotificationsJob" should {
    import scala.concurrent.ExecutionContext.Implicits.global
    val boxId = BoxId(UUID.randomUUID)
    val boxName: String = "boxName"
    val clientId: ClientId = ClientId(UUID.randomUUID.toString)
    val subscriber: PushSubscriber = PushSubscriber("somecallbackUrl", now)
    val box: Box = Box(boxId, boxName, BoxCreator(clientId),  subscriber = Some(subscriber))

    "retry pushing the notifications" in new Setup {
      val notification: Notification = Notification(NotificationId(UUID.randomUUID()),
        BoxId(UUID.randomUUID()), MessageContentType.APPLICATION_JSON, "{}", NotificationStatus.FAILED)
      val retryableNotification: RetryableNotification = RetryableNotification(notification, box)
      when(mockNotificationsRepository.fetchRetryableNotifications)
        .thenReturn(Source.future(successful(retryableNotification)))
      when(mockNotificationPushService.handlePushNotification(*, *)(*, *)).thenReturn(successful(true))

      val result: underTest.Result = await(underTest.execute)

      verify(mockNotificationPushService, times(1)).handlePushNotification(eqTo(box), eqTo(notification))(*, *)
      result.message shouldBe "RetryPushNotificationsJob Job ran successfully."
    }

    "set notification RetryAfterDateTime when it fails to push and the notification is not too old for further retries" in new Setup {
      val notification: Notification = Notification(NotificationId(UUID.randomUUID()),
        BoxId(UUID.randomUUID()), MessageContentType.APPLICATION_JSON, "{}", NotificationStatus.FAILED, now(UTC).minusHours(5))
      val retryableNotification: RetryableNotification = RetryableNotification(notification, box)
      when(mockNotificationsRepository.fetchRetryableNotifications)
        .thenReturn(Source.future(successful(retryableNotification)))
      when(mockNotificationsRepository.updateRetryAfterDateTime(NotificationId(*), *)).thenReturn(successful(notification))
      when(mockNotificationPushService.handlePushNotification(*, *)(*, *)).thenReturn(successful(false))

      val result: underTest.Result = await(underTest.execute)

      verify(mockNotificationsRepository, times(1)).updateRetryAfterDateTime(NotificationId(*), *)
      result.message shouldBe "RetryPushNotificationsJob Job ran successfully."
    }

    "set notification status to failed when it fails to push and the notification is too old for further retries" in new Setup {
      val notification: Notification = Notification(NotificationId(UUID.randomUUID()),
        BoxId(UUID.randomUUID()), MessageContentType.APPLICATION_JSON, "{}", NotificationStatus.FAILED, now(UTC).minusHours(7))
      val retryableNotification: RetryableNotification = RetryableNotification(notification, box)
      when(mockNotificationsRepository.fetchRetryableNotifications)
        .thenReturn(Source.future(successful(retryableNotification)))
      when(mockNotificationsRepository.updateStatus(notification.notificationId, FAILED)).thenReturn(successful(notification))
      when(mockNotificationPushService.handlePushNotification(*, *)(*, *)).thenReturn(successful(false))

      val result: underTest.Result = await(underTest.execute)

      verify(mockNotificationsRepository, times(1)).updateStatus(notification.notificationId, FAILED)
      verify(mockNotificationsRepository, never).updateRetryAfterDateTime(NotificationId(*), *)
      result.message shouldBe "RetryPushNotificationsJob Job ran successfully."
    }

    "not execute if the job is already running" in new Setup {
      val notification: Notification = Notification(NotificationId(UUID.randomUUID()),
        BoxId(UUID.randomUUID()), MessageContentType.APPLICATION_JSON, "{}", NotificationStatus.FAILED, now(UTC).minusHours(7))
      val retryableNotification: RetryableNotification = RetryableNotification(notification, box)
      when(mockNotificationPushService.handlePushNotification(*, *)(*, *)).thenReturn(successful(true))
      when(mockNotificationsRepository.fetchRetryableNotifications)
        .thenReturn(Source.future(successful(retryableNotification)))
      when(mongoLockRepo.isLocked(*, *)).thenReturn(successful(true)).andThenAnswer(successful(true)).andThenAnswer(successful(false))
      when(mongoLockRepo.takeLock(*, *, *)).thenReturn(successful(true)).andThenAnswer(successful(false))
      when(mongoLockRepo.releaseLock(*, *)).thenReturn(successful(true))

      val result: underTest.Result = await(underTest.execute)
      val result2: underTest.Result = await(underTest.execute)

      verify(mockNotificationsRepository, times(1)).fetchRetryableNotifications
      verify(mockNotificationPushService, times(1)).handlePushNotification(*, *)(*, *)
      result2.message shouldBe "RetryPushNotificationsJob did not run because repository was locked by another instance of the scheduler."
    }

    "handle error when something fails" in new Setup {
      when(mockNotificationsRepository.fetchRetryableNotifications)
        .thenReturn(Source.future(failed(new RuntimeException("Failed"))))

      val result: underTest.Result = await(underTest.execute)

      verify(mockNotificationPushService, never).handlePushNotification(*, *)(*, *)
      result.message shouldBe "The execution of scheduled job RetryPushNotificationsJob failed with error 'Failed'. " +
        "The next execution of the job will do retry."
    }
  }
}

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

package uk.gov.hmrc.pushpullnotificationsapi.services

import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

import com.codahale.metrics.{Counter, MetricRegistry, Timer}
import org.scalatest.concurrent.Eventually
import org.scalatest.time._

import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.mocks._
import uk.gov.hmrc.pushpullnotificationsapi.mocks.repository.{BoxRepositoryMockModule, NotificationsRepositoryMockModule}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications._
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

class NotificationPushServiceSpec extends AsyncHmrcSpec with TestData with FixedClock with Eventually with SpanSugar {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup
      extends PushServiceMockModule
      with ConfirmationServiceMockModule
      with BoxRepositoryMockModule
      with NotificationsRepositoryMockModule
      with ClientServiceMockModule
      with HmacServiceMockModule {

    val mockMetrics: Metrics = mock[Metrics]
    val mockRegistry = mock[MetricRegistry]
    val mockTimer = mock[Timer]
    val mockCounter = mock[Counter]
    when(mockMetrics.defaultRegistry).thenReturn(mockRegistry)
    when(mockRegistry.timer(*)).thenReturn(mockTimer)
    when(mockRegistry.counter(*)).thenReturn(mockCounter)

    val serviceToTest = new NotificationPushService(
      PushServiceMock.aMock,
      NotificationsRepositoryMock.aMock,
      BoxRepositoryMock.aMock,
      ClientServiceMock.aMock,
      HmacServiceMock.aMock,
      ConfirmationServiceMock.aMock,
      mockMetrics,
      FixedClock.clock
    )
  }

  "handlePushNotification" should {

    def checkOutboundNotificationIsCorrect(originalNotification: Notification, subscriber: PushSubscriber, sentOutboundNotification: OutboundNotification) = {
      sentOutboundNotification.destinationUrl shouldBe subscriber.callBackUrl
      val stringCreated = new DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd'T'HH:mm:ss.SSSZ")
        .toFormatter
        .withZone(ZoneId.of("UTC")).format(originalNotification.createdDateTime)

      val jsonPayload = Json.parse(sentOutboundNotification.payload)
      (jsonPayload \ "notificationId").as[String] shouldBe originalNotification.notificationId.value.toString
      (jsonPayload \ "boxId").as[UUID] shouldBe originalNotification.boxId.value
      (jsonPayload \ "messageContentType").as[String] shouldBe originalNotification.messageContentType.value
      (jsonPayload \ "message").as[String] shouldBe originalNotification.message
      (jsonPayload \ "status").as[NotificationStatus] shouldBe originalNotification.status
      (jsonPayload \ "createdDateTime").as[String] shouldBe stringCreated
    }

    "return true when connector returns success result and update the notification status to ACKNOWLEDGED" in new Setup {

      NotificationsRepositoryMock.UpdateStatus.succeedsFor(notification, acknowledgedNotificationStatus)
      ConfirmationServiceMock.HandleConfirmation.thenSuccessFor(notificationId)

      val outboundNotificationCaptor = PushServiceMock.HandleNotification.succeedsFor()
      ClientServiceMock.FindOrCreateClient.isSuccessWith(clientId, client)

      val result: Boolean = await(serviceToTest.handlePushNotification(BoxObjectWithPushSubscribers, notification))

      checkOutboundNotificationIsCorrect(notification, pushSubscriber, outboundNotificationCaptor.value)
      result shouldBe true

      NotificationsRepositoryMock.UpdateStatus.verifyCalledWith(notificationId, acknowledgedNotificationStatus)
      ConfirmationServiceMock.HandleConfirmation.verifyCalledWith(notificationId)
    }

    "put the notification signature in the forwarded headers" in new Setup {
      val expectedSignature = "the signature"
      HmacServiceMock.Sign.succeedsWith(expectedSignature)
      ConfirmationServiceMock.HandleConfirmation.thenSuccessFor(notificationId)
      val outboundNotificationCaptor = PushServiceMock.HandleNotification.succeedsFor()
      ClientServiceMock.FindOrCreateClient.isSuccessWith(clientId, client)
      NotificationsRepositoryMock.UpdateStatus.succeedsFor(notification, acknowledgedNotificationStatus)

      await(serviceToTest.handlePushNotification(BoxObjectWithPushSubscribers, notification))

      outboundNotificationCaptor.value.forwardedHeaders should contain(ForwardedHeader("X-Hub-Signature", expectedSignature))
    }

    "return false when connector returns failed result due to exception" in new Setup {

      ClientServiceMock.FindOrCreateClient.isSuccessWith(clientId, client)
      val outboundNotificationCaptor = PushServiceMock.HandleNotification.fails()

      val result: Boolean = await(serviceToTest.handlePushNotification(BoxObjectWithPushSubscribers, notification))

      checkOutboundNotificationIsCorrect(notification, pushSubscriber, outboundNotificationCaptor.value)
      result shouldBe false
    }

    "not try to update the notification status to FAILED when the connector fails but the notification already had the status FAILED" in new Setup {
      ClientServiceMock.FindOrCreateClient.isSuccessWith(clientId, client)

      val outboundNotificationCaptor = PushServiceMock.HandleNotification.fails()

      val result: Boolean = await(serviceToTest.handlePushNotification(BoxObjectWithPushSubscribers, failedNotification))

      checkOutboundNotificationIsCorrect(failedNotification, pushSubscriber, outboundNotificationCaptor.value)
      result shouldBe false
      NotificationsRepositoryMock.verifyZeroInteractions()
    }

    "return true when subscriber has no callback url" in new Setup {
      ClientServiceMock.FindOrCreateClient.isSuccessWith(clientId, client)

      val result: Boolean = await(serviceToTest.handlePushNotification(BoxObjectWithPushSubscribers.copy(subscriber = Some(PushSubscriber(""))), notification))

      result shouldBe true
      PushServiceMock.verifyZeroInteractions()
      NotificationsRepositoryMock.verifyZeroInteractions()
    }

    "return true when there are no push subscribers" in new Setup {
      val subscriber: PullSubscriber = PullSubscriber("", instant)
      val box: Box = Box(boxId, boxName, BoxCreator(clientId), subscriber = Some(subscriber))
      val notification: Notification =
        Notification(NotificationId(UUID.randomUUID()), BoxId(UUID.randomUUID()), MessageContentType.APPLICATION_JSON, "{}", NotificationStatus.PENDING)

      val result: Boolean = await(serviceToTest.handlePushNotification(box, notification))

      result shouldBe true
      PushServiceMock.verifyZeroInteractions()
      NotificationsRepositoryMock.verifyZeroInteractions()
    }
  }
}

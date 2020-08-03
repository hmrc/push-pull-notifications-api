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

package uk.gov.hmrc.pushpullnotificationsapi.services

import java.util.UUID

import org.joda.time.DateTime
import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.connectors.PushConnector
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.ACKNOWLEDGED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications._
import uk.gov.hmrc.pushpullnotificationsapi.repository.NotificationsRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class NotificationPushServiceSpec extends UnitSpec with MockitoSugar with ArgumentMatchersSugar with  BeforeAndAfterEach {

  private val mockConnector = mock[PushConnector]
  private val mockNotificationsRepo = mock[NotificationsRepository]
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConnector, mockNotificationsRepo)
  }

  trait Setup {
     val serviceToTest = new NotificationPushService(mockConnector, mockNotificationsRepo)
    }


  "handlePushNotification" should {

    def checkOutboundNotificationIsCorrect(originalNotification: Notification, subscriber: PushSubscriber, sentOutboundNotification: OutboundNotification) = {
      sentOutboundNotification.destinationUrl shouldBe subscriber.callBackUrl

      val jsonPayload = Json.toJson(sentOutboundNotification.payload)
      (jsonPayload \ "notificationId").as[String] shouldBe originalNotification.notificationId.value.toString
      (jsonPayload \ "boxId").as[UUID] shouldBe originalNotification.boxId.value
      (jsonPayload \ "messageContentType").as[String] shouldBe originalNotification.messageContentType.value
      (jsonPayload \ "message").as[String] shouldBe originalNotification.message
      (jsonPayload \ "status").as[NotificationStatus] shouldBe originalNotification.status
      (jsonPayload \ "createdDateTime").as[DateTime].getMillis shouldBe originalNotification.createdDateTime.getMillis
    }

    "return true when connector returns success result and update the notification status to ACKNOWLEDGED" in new Setup {
      val outboundNotificationCaptor: ArgumentCaptor[OutboundNotification] = ArgumentCaptor.forClass(classOf[OutboundNotification])
      when(mockConnector.send(outboundNotificationCaptor.capture())(any)).thenReturn(Future.successful(PushConnectorSuccessResult()))

      val subscriber = PushSubscriber("somecallbackUrl", DateTime.now)
      val notification: Notification =
        Notification(
          NotificationId(UUID.randomUUID()),
          BoxId(UUID.randomUUID()),
          MessageContentType.APPLICATION_JSON,
          """{ "foo": "bar" }""",
          NotificationStatus.PENDING)

      val result: Boolean = await(serviceToTest.handlePushNotification(subscriber, notification))

      checkOutboundNotificationIsCorrect(notification, subscriber, outboundNotificationCaptor.getValue)
      result shouldBe true
      verify(mockNotificationsRepo).updateStatus(notification.notificationId, ACKNOWLEDGED)
    }

    "return false when connector returns failed result due to exception" in new Setup {
      val outboundNotificationCaptor: ArgumentCaptor[OutboundNotification] = ArgumentCaptor.forClass(classOf[OutboundNotification])
      when(mockConnector.send(outboundNotificationCaptor.capture())(any[HeaderCarrier]))
        .thenReturn(Future.successful(PushConnectorFailedResult(new IllegalArgumentException())))

      val subscriber = PushSubscriber("somecallbackUrl", DateTime.now)
      val notification: Notification = Notification(NotificationId(UUID.randomUUID()),
        BoxId(UUID.randomUUID()),
        MessageContentType.APPLICATION_JSON,
        "{}", NotificationStatus.PENDING)

      val result: Boolean = await(serviceToTest.handlePushNotification(subscriber, notification))

      checkOutboundNotificationIsCorrect(notification, subscriber, outboundNotificationCaptor.getValue)
      result shouldBe false
    }

    "not try to update the notification status to FAILED when the connector fails but the notification already had the status FAILED" in new Setup {
      val outboundNotificationCaptor: ArgumentCaptor[OutboundNotification] = ArgumentCaptor.forClass(classOf[OutboundNotification])
      when(mockConnector.send(outboundNotificationCaptor.capture())(any[HeaderCarrier]))
        .thenReturn(Future.successful(PushConnectorFailedResult(new IllegalArgumentException())))

      val subscriber = PushSubscriber("somecallbackUrl", DateTime.now)
      val notification: Notification = Notification(NotificationId(UUID.randomUUID()),
        BoxId(UUID.randomUUID()),
        MessageContentType.APPLICATION_JSON,
        "{}", NotificationStatus.FAILED)

      val result: Boolean = await(serviceToTest.handlePushNotification(subscriber, notification))

      checkOutboundNotificationIsCorrect(notification, subscriber, outboundNotificationCaptor.getValue)
      result shouldBe false
      verifyZeroInteractions(mockNotificationsRepo)
    }

    "return true when subscriber has no callback url" in new Setup {
      val subscriber = PushSubscriber("", DateTime.now)
      val notification: Notification = Notification(NotificationId(UUID.randomUUID()),
        BoxId(UUID.randomUUID()),
        MessageContentType.APPLICATION_JSON,
        "{}", NotificationStatus.PENDING)

      val result: Boolean = await(serviceToTest.handlePushNotification(subscriber, notification))

      result shouldBe true
      verifyZeroInteractions(mockConnector)
      verifyZeroInteractions(mockNotificationsRepo)
    }

    "return true when there are no push subscribers" in new Setup {
      val subscriber = PullSubscriber("", DateTime.now)
      val notification: Notification = Notification(NotificationId(UUID.randomUUID()),
        BoxId(UUID.randomUUID()),
        MessageContentType.APPLICATION_JSON,
        "{}", NotificationStatus.PENDING)

      val result: Boolean = await(serviceToTest.handlePushNotification(subscriber, notification))

      result shouldBe true
      verifyZeroInteractions(mockConnector)
      verifyZeroInteractions(mockNotificationsRepo)
    }
  }
}

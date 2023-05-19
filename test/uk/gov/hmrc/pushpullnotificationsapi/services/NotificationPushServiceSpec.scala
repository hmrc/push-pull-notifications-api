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

package uk.gov.hmrc.pushpullnotificationsapi.services

import java.time.format.DateTimeFormatterBuilder
import java.time.{Instant, ZoneId}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import org.mockito.captor.ArgCaptor
import org.scalatest.BeforeAndAfterEach

import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.connectors.PushConnector
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.ACKNOWLEDGED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications._
import uk.gov.hmrc.pushpullnotificationsapi.repository.NotificationsRepository
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientSecret

class NotificationPushServiceSpec extends AsyncHmrcSpec with BeforeAndAfterEach {

  private val mockConnector = mock[PushConnector]
  private val mockNotificationsRepo = mock[NotificationsRepository]
  private val mockBoxRepo = mock[BoxRepository]
  private val mockClientService = mock[ClientService]
  private val mockHmacService = mock[HmacService]
  private val mockConfirmationService = mock[ConfirmationService]
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConnector, mockNotificationsRepo)
  }

  trait Setup {
    val serviceToTest = new NotificationPushService(mockConnector, mockNotificationsRepo, mockBoxRepo, mockClientService, mockHmacService, mockConfirmationService)
  }

  "handlePushNotification" should {
    val boxId = BoxId(UUID.randomUUID)
    val boxName: String = "boxName"
    val clientId: ClientId = ClientId(UUID.randomUUID.toString)
    val clientSecret: ClientSecretValue = ClientSecretValue("someRandomSecret")
    val client: Client = Client(clientId, Seq(clientSecret))

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
      val outboundNotificationCaptor = ArgCaptor[OutboundNotification]

      val subscriber: PushSubscriber = PushSubscriber("somecallbackUrl", Instant.now)
      val box: Box = Box(boxId, boxName, BoxCreator(clientId), subscriber = Some(subscriber))
      val notification: Notification =
        Notification(
          NotificationId(UUID.randomUUID()),
          BoxId(UUID.randomUUID()),
          MessageContentType.APPLICATION_JSON,
          """{ "foo": "bar" }""",
          NotificationStatus.PENDING
        )

      when(mockNotificationsRepo.updateStatus(*[NotificationId], *)).thenReturn(successful(mock[Notification]))
      when(mockConnector.send(outboundNotificationCaptor)(*)).thenReturn(successful(PushConnectorSuccessResult()))
      when(mockClientService.findOrCreateClient(clientId)).thenReturn(successful(client))

      val result: Boolean = await(serviceToTest.handlePushNotification(box, notification))

      checkOutboundNotificationIsCorrect(notification, subscriber, outboundNotificationCaptor.value)
      result shouldBe true
      verify(mockNotificationsRepo).updateStatus(notification.notificationId, ACKNOWLEDGED)
      verify(mockConfirmationService).handleConfirmation(notification.notificationId)
    }

    "put the notification signature in the forwarded headers" in new Setup {
      val expectedSignature = "the signature"
      when(mockHmacService.sign(any, any)).thenReturn(expectedSignature)
      val outboundNotificationCaptor = ArgCaptor[OutboundNotification]
      when(mockConnector.send(outboundNotificationCaptor)(*)).thenReturn(successful(PushConnectorSuccessResult()))
      when(mockClientService.findOrCreateClient(clientId)).thenReturn(successful(client))
      when(mockNotificationsRepo.updateStatus(*[NotificationId], *)).thenReturn(successful(mock[Notification]))
      val subscriber: PushSubscriber = PushSubscriber("somecallbackUrl", Instant.now)
      val box: Box = Box(boxId, boxName, BoxCreator(clientId), subscriber = Some(subscriber))
      val notification: Notification =
        Notification(
          NotificationId(UUID.randomUUID()),
          BoxId(UUID.randomUUID()),
          MessageContentType.APPLICATION_JSON,
          """{ "foo": "bar" }""",
          NotificationStatus.PENDING
        )

      await(serviceToTest.handlePushNotification(box, notification))

      outboundNotificationCaptor.value.forwardedHeaders should contain(ForwardedHeader("X-Hub-Signature", expectedSignature))
    }

    "return false when connector returns failed result due to exception" in new Setup {
      val outboundNotificationCaptor = ArgCaptor[OutboundNotification]
      when(mockConnector.send(outboundNotificationCaptor)(*))
        .thenReturn(successful(PushConnectorFailedResult("some error")))

      val subscriber: PushSubscriber = PushSubscriber("somecallbackUrl", Instant.now)
      val box: Box = Box(boxId, boxName, BoxCreator(clientId), subscriber = Some(subscriber))
      val notification: Notification =
        Notification(NotificationId(UUID.randomUUID()), BoxId(UUID.randomUUID()), MessageContentType.APPLICATION_JSON, "{}", NotificationStatus.PENDING)

      val result: Boolean = await(serviceToTest.handlePushNotification(box, notification))

      checkOutboundNotificationIsCorrect(notification, subscriber, outboundNotificationCaptor.value)
      result shouldBe false
    }

    "not try to update the notification status to FAILED when the connector fails but the notification already had the status FAILED" in new Setup {
      val outboundNotificationCaptor = ArgCaptor[OutboundNotification]
      when(mockConnector.send(outboundNotificationCaptor)(*))
        .thenReturn(successful(PushConnectorFailedResult("Some Error")))

      val subscriber: PushSubscriber = PushSubscriber("somecallbackUrl", Instant.now)
      val box: Box = Box(boxId, boxName, BoxCreator(clientId), subscriber = Some(subscriber))
      val notification: Notification =
        Notification(NotificationId(UUID.randomUUID()), BoxId(UUID.randomUUID()), MessageContentType.APPLICATION_JSON, "{}", NotificationStatus.FAILED)

      val result: Boolean = await(serviceToTest.handlePushNotification(box, notification))

      checkOutboundNotificationIsCorrect(notification, subscriber, outboundNotificationCaptor.value)
      result shouldBe false
      verifyZeroInteractions(mockNotificationsRepo)
    }

    "return true when subscriber has no callback url" in new Setup {
      val subscriber: PushSubscriber = PushSubscriber("", Instant.now)
      val box: Box = Box(boxId, boxName, BoxCreator(clientId), subscriber = Some(subscriber))
      val notification: Notification =
        Notification(NotificationId(UUID.randomUUID()), BoxId(UUID.randomUUID()), MessageContentType.APPLICATION_JSON, "{}", NotificationStatus.PENDING)

      val result: Boolean = await(serviceToTest.handlePushNotification(box, notification))

      result shouldBe true
      verifyZeroInteractions(mockConnector)
      verifyZeroInteractions(mockNotificationsRepo)
    }

    "return true when there are no push subscribers" in new Setup {
      val subscriber: PullSubscriber = PullSubscriber("", Instant.now)
      val box: Box = Box(boxId, boxName, BoxCreator(clientId), subscriber = Some(subscriber))
      val notification: Notification =
        Notification(NotificationId(UUID.randomUUID()), BoxId(UUID.randomUUID()), MessageContentType.APPLICATION_JSON, "{}", NotificationStatus.PENDING)

      val result: Boolean = await(serviceToTest.handlePushNotification(box, notification))

      result shouldBe true
      verifyZeroInteractions(mockConnector)
      verifyZeroInteractions(mockNotificationsRepo)
    }
  }
}

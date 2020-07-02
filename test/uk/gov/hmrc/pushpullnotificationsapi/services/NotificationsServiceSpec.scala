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
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.mockito.captor.{ArgCaptor, Captor}
import org.mockito.stubbing.ScalaOngoingStubbing
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.{BoxRepository, NotificationsRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class NotificationsServiceSpec extends UnitSpec with MockitoSugar with ArgumentMatchersSugar with  BeforeAndAfterEach {

  private val mockBoxRepo = mock[BoxRepository]
  private val mockNotificationsRepo = mock[NotificationsRepository]
  private val mockNotificationsPushService = mock[NotificationPushService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockBoxRepo, mockNotificationsRepo, mockNotificationsPushService)
  }

  trait Setup {

    val serviceToTest = new NotificationsService(mockBoxRepo, mockNotificationsRepo, mockNotificationsPushService)
    val notificationCaptor: Captor[Notification] = ArgCaptor[Notification]

    def primeNotificationRepoSave(result: Future[Option[NotificationId]]): ScalaOngoingStubbing[Future[Option[NotificationId]]] = {
      when(mockNotificationsRepo.saveNotification(any[Notification])(any[ExecutionContext])).thenReturn(result)
    }
    def primeNotificationRepoGetNotifications(result: Future[List[Notification]]): ScalaOngoingStubbing[Future[List[Notification]]] = {
      when(mockNotificationsRepo.getByBoxIdAndFilters(eqTo(boxId),
        any[Option[NotificationStatus]],
        any[Option[DateTime]],
        any[Option[DateTime]])(any[ExecutionContext])).thenReturn(result)
    }


    def primeBoxRepo(result: Future[List[Box]], boxId: BoxId): ScalaOngoingStubbing[Future[List[Box]]] = {
      when(mockBoxRepo.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(result)
    }
  }

  private val boxIdStr = "ea69654b-9041-42c9-be5c-68dc11ecbcdf"
  private val boxId = models.BoxId(UUID.fromString(boxIdStr))
  private val clientIdStr = "b15b81ff-536b-4292-ae84-9466af9f3ab1"
  private val clientId = ClientId(clientIdStr)
  private val messageContentTypeJson = MessageContentType.APPLICATION_JSON
  private val messageContentTypeXml = MessageContentType.APPLICATION_XML
  private val message = "message"
  private val subscriberList =  List(PushSubscriber("mycallbackUrl", subscriberId = SubscriberId(UUID.randomUUID())))
  private val BoxObjectWIthNoSubscribers = Box(boxId, "boxName", BoxCreator(clientId))
  private val BoxObjectWIthSubscribers = Box(boxId, "boxName", BoxCreator(clientId),subscriberList)
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "SaveNotification" should {
    "return NotificationCreateSuccessResult when box exists , push is called with List of subscribers  & notification successfully saved" in new Setup {
      primeBoxRepo(Future.successful(List(BoxObjectWIthSubscribers)), boxId)
      primeNotificationRepoSave(Future.successful(Some(NotificationId(UUID.randomUUID()))))
      when(mockNotificationsPushService.handlePushNotification(eqTo(subscriberList),  any[Notification])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      val result: NotificationCreateServiceResult = await(serviceToTest.saveNotification(boxId,
        NotificationId(UUID.randomUUID()), messageContentTypeJson, message))
      result shouldBe NotificationCreateSuccessResult()

      verify(mockBoxRepo, times(1)).findByBoxId(eqTo(boxId))(any[ExecutionContext])
      verify(mockNotificationsPushService).handlePushNotification(eqTo(subscriberList), any[Notification])(any[HeaderCarrier], any[ExecutionContext])
      verify(mockNotificationsRepo).saveNotification(notificationCaptor.capture)(any[ExecutionContext])
      notificationCaptor.value.boxId shouldBe boxId
      notificationCaptor.value.messageContentType shouldBe messageContentTypeJson
      notificationCaptor.value.message shouldBe message


    }

    "return NotificationCreateSuccessResult when box exists, push is called with Empty List &  notification successfully saved" in new Setup {
      primeBoxRepo(Future.successful(List(BoxObjectWIthNoSubscribers)), boxId)
      primeNotificationRepoSave(Future.successful(Some(NotificationId(UUID.randomUUID()))))

      val result: NotificationCreateServiceResult = await(serviceToTest.saveNotification(boxId,
        NotificationId(UUID.randomUUID()), messageContentTypeJson, message))
      result shouldBe NotificationCreateSuccessResult()

      verify(mockBoxRepo, times(1)).findByBoxId(eqTo(boxId))(any[ExecutionContext])
      verifyNoMoreInteractions(mockNotificationsPushService)
      verify(mockNotificationsRepo).saveNotification(notificationCaptor.capture)(any[ExecutionContext])
      notificationCaptor.value.boxId shouldBe boxId
      notificationCaptor.value.messageContentType shouldBe messageContentTypeJson
      notificationCaptor.value.message shouldBe message


    }

    "return NotificationCreateFailedBoxNotFoundResult when box does not exist" in new Setup {
      primeBoxRepo(Future.successful(List.empty), boxId)

      val result: NotificationCreateServiceResult =
        await(serviceToTest.saveNotification(boxId, NotificationId(UUID.randomUUID()), messageContentTypeXml, message))
      result shouldBe NotificationCreateFailedBoxIdNotFoundResult(s"BoxId: BoxId($boxIdStr) not found")

      verify(mockBoxRepo, times(1)).findByBoxId(eqTo(boxId))(any[ExecutionContext])
      verifyZeroInteractions(mockNotificationsRepo)
    }
  }

  "getNotifications" should {

    val status = Some(NotificationStatus.RECEIVED)
    val fromDate = Some(DateTime.now().minusHours(2))
    val toDate = Some(DateTime.now())


    "return list of matched notifications" in new Setup {

      primeBoxRepo(Future.successful(List(BoxObjectWIthNoSubscribers)), boxId)
      primeNotificationRepoGetNotifications(
        Future.successful(List(Notification(NotificationId(UUID.randomUUID()),
          boxId,MessageContentType.APPLICATION_JSON, "{}", status.head, toDate.head)))
      )
     val result: GetNotificationCreateServiceResult =  await(serviceToTest.getNotifications(boxId= boxId,
        clientId = clientId,
        status = status,
        fromDateTime = fromDate,
        toDateTime = toDate))

      val resultsList : GetNotificationsSuccessRetrievedResult = result.asInstanceOf[GetNotificationsSuccessRetrievedResult]
      resultsList.notifications.isEmpty shouldBe false

      verify(mockNotificationsRepo).getByBoxIdAndFilters(eqTo(boxId), eqTo(status), eqTo(fromDate), eqTo(toDate))(any[ExecutionContext])

    }

    "return empty list when no notifications found" in new Setup {

      primeBoxRepo(Future.successful(List(BoxObjectWIthNoSubscribers)), boxId)
      primeNotificationRepoGetNotifications(Future.successful(List.empty))

      val result: GetNotificationCreateServiceResult =  await(serviceToTest.getNotifications(boxId= boxId,
        clientId = clientId,
        status = status,
        fromDateTime = fromDate,
        toDateTime = toDate))

      result shouldBe GetNotificationsSuccessRetrievedResult(List.empty)

      verify(mockNotificationsRepo).getByBoxIdAndFilters(eqTo(boxId), eqTo(status), eqTo(fromDate), eqTo(toDate))(any[ExecutionContext])
    }

    "return notfound exception when client id is different from box creator client id" in new Setup {

      primeBoxRepo(Future.successful(List(BoxObjectWIthNoSubscribers)), boxId)
      primeNotificationRepoGetNotifications(Future.successful(List.empty))

      val result: GetNotificationCreateServiceResult = await(serviceToTest.getNotifications(boxId = boxId,
          clientId = ClientId(UUID.randomUUID().toString),
          status = status,
          fromDateTime = fromDate,
          toDateTime = toDate))

      result shouldBe GetNotificationsServiceUnauthorisedResult("clientId does not match boxCreator")

      verifyNoMoreInteractions(mockNotificationsRepo)
    }
  }
}

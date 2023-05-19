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

import java.time.{Duration, Instant}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.mockito.captor.{ArgCaptor, Captor}
import org.scalatest.BeforeAndAfterEach

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{BoxRepository, NotificationsRepository}
import uk.gov.hmrc.pushpullnotificationsapi.{models, AsyncHmrcSpec}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

class NotificationsServiceSpec extends AsyncHmrcSpec with BeforeAndAfterEach {

  private val mockBoxRepo = mock[BoxRepository]
  private val mockNotificationsRepo = mock[NotificationsRepository]
  private val mockNotificationsPushService = mock[NotificationPushService]
  private val mockConfirmationService = mock[ConfirmationService]
  val serviceToTest = new NotificationsService(mockBoxRepo, mockNotificationsRepo, mockNotificationsPushService, mockConfirmationService)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockBoxRepo, mockNotificationsRepo, mockNotificationsPushService)
  }

  trait Setup {

    val notificationCaptor: Captor[Notification] = ArgCaptor[Notification]

    // API-4417: Default the number of notifications
    when(mockNotificationsRepo.numberOfNotificationsToReturn).thenReturn(100)

    def primeNotificationRepoSave(result: Future[Option[NotificationId]]) = {
      when(mockNotificationsRepo.saveNotification(*)(*)).thenReturn(result)
    }

    def primeNotificationRepoGetNotifications(result: Future[List[Notification]]) = {
      when(mockNotificationsRepo.getByBoxIdAndFilters(eqTo(boxId), *, *, *, *)(*)).thenReturn(result)
    }

    def primeBoxRepo(result: Future[Option[Box]], boxId: BoxId) = {
      when(mockBoxRepo.findByBoxId(eqTo(boxId))).thenReturn(result)
    }
  }

  private val boxIdStr = "ea69654b-9041-42c9-be5c-68dc11ecbcdf"
  private val boxId = models.BoxId(UUID.fromString(boxIdStr))
  private val clientIdStr = "b15b81ff-536b-4292-ae84-9466af9f3ab1"
  private val clientId = ClientId(clientIdStr)
  private val messageContentTypeJson = MessageContentType.APPLICATION_JSON
  private val messageContentTypeXml = MessageContentType.APPLICATION_XML
  private val message = "message"
  private val subscriber = PushSubscriber("mycallbackUrl")
  private val BoxObjectWithNoSubscribers = Box(boxId, "boxName", BoxCreator(clientId))
  private val BoxObjectWithSubscribers = Box(boxId, "boxName", BoxCreator(clientId), subscriber = Some(subscriber))
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "SaveNotification" should {
    "return NotificationCreateSuccessResult when box exists , push is called with subscriber & notification successfully saved" in new Setup {
      primeBoxRepo(Future.successful(Some(BoxObjectWithSubscribers)), boxId)
      primeNotificationRepoSave(Future.successful(Some(NotificationId.random)))
      when(mockNotificationsPushService.handlePushNotification(eqTo(BoxObjectWithSubscribers), *)(*, *))
        .thenReturn(Future.successful(true))
      val result: NotificationCreateServiceResult = await(serviceToTest.saveNotification(boxId, NotificationId.random, messageContentTypeJson, message))
      result shouldBe NotificationCreateSuccessResult()

      verify(mockBoxRepo, times(1)).findByBoxId(eqTo(boxId))
      verify(mockNotificationsPushService).handlePushNotification(eqTo(BoxObjectWithSubscribers), *)(*, *)
      validateNotificationSaved(notificationCaptor)
    }

    "return NotificationCreateSuccessResult when box exists, push is called with empty subscriber & notification successfully saved" in new Setup {
      primeBoxRepo(Future.successful(Some(BoxObjectWithNoSubscribers)), boxId)
      primeNotificationRepoSave(Future.successful(Some(NotificationId.random)))

      val result: NotificationCreateServiceResult = await(serviceToTest.saveNotification(boxId, NotificationId.random, messageContentTypeJson, message))
      result shouldBe NotificationCreateSuccessResult()

      verify(mockBoxRepo, times(1)).findByBoxId(eqTo(boxId))
      verify(mockNotificationsPushService).handlePushNotification(eqTo(BoxObjectWithNoSubscribers), *)(*, *)
      validateNotificationSaved(notificationCaptor)
    }

    "return NotificationCreateFailedBoxNotFoundResult when box does not exist" in new Setup {
      primeBoxRepo(Future.successful(None), boxId)

      val result: NotificationCreateServiceResult =
        await(serviceToTest.saveNotification(boxId, NotificationId.random, messageContentTypeXml, message))
      result shouldBe NotificationCreateFailedBoxIdNotFoundResult(s"BoxId: BoxId($boxIdStr) not found")

      verify(mockBoxRepo, times(1)).findByBoxId(eqTo(boxId))
      verifyZeroInteractions(mockNotificationsRepo)
    }

    def validateNotificationSaved(notificationCaptor: Captor[Notification]): Unit = {
      verify(mockNotificationsRepo).saveNotification(notificationCaptor)(*)
      notificationCaptor.value.boxId shouldBe boxId
      notificationCaptor.value.messageContentType shouldBe messageContentTypeJson
      notificationCaptor.value.message shouldBe message
    }
  }

  "getNotifications" should {

    val status = Some(NotificationStatus.PENDING)
    val fromDate = Some(Instant.now.minus(Duration.ofHours(2)))
    val toDate = Some(Instant.now)

    "return list of matched notifications" in new Setup {

      primeBoxRepo(Future.successful(Some(BoxObjectWithNoSubscribers)), boxId)
      primeNotificationRepoGetNotifications(
        Future.successful(List(Notification(NotificationId.random, boxId, MessageContentType.APPLICATION_JSON, "{}", status.head, toDate.head)))
      )
      val result: Either[GetNotificationsServiceFailedResult, List[Notification]] =
        await(serviceToTest.getNotifications(boxId = boxId, clientId = clientId, status = status, fromDateTime = fromDate, toDateTime = toDate))

      result match {
        case Right(g: List[Notification]) => g.size shouldBe 1
        case _ => fail()
      }

      verify(mockNotificationsRepo).getByBoxIdAndFilters(eqTo(boxId), eqTo(status), eqTo(fromDate), eqTo(toDate), anyInt)(*)

    }

    "return empty list when no notifications found" in new Setup {

      primeBoxRepo(Future.successful(Some(BoxObjectWithNoSubscribers)), boxId)
      primeNotificationRepoGetNotifications(Future.successful(List.empty))

      val result: Either[GetNotificationsServiceFailedResult, List[Notification]] =
        await(serviceToTest.getNotifications(boxId = boxId, clientId = clientId, status = status, fromDateTime = fromDate, toDateTime = toDate))

      result shouldBe Right(List.empty)

      verify(mockNotificationsRepo).getByBoxIdAndFilters(eqTo(boxId), eqTo(status), eqTo(fromDate), eqTo(toDate), anyInt)(*)
    }

    "return notfound exception when client id is different from box creator client id" in new Setup {

      primeBoxRepo(Future.successful(Some(BoxObjectWithNoSubscribers)), boxId)
      primeNotificationRepoGetNotifications(Future.successful(List.empty))

      val result: Either[GetNotificationsServiceFailedResult, List[Notification]] =
        await(serviceToTest.getNotifications(boxId = boxId, clientId = ClientId(UUID.randomUUID().toString), status = status, fromDateTime = fromDate, toDateTime = toDate))

      result shouldBe Left(GetNotificationsServiceUnauthorisedResult("clientId does not match boxCreator"))

      verifyNoMoreInteractions(mockNotificationsRepo)
    }
  }

  "Acknowledge notifications" should {
    "return AcknowledgeNotificationsSuccessUpdatedResult when repo returns true" in new Setup {
      primeBoxRepo(Future.successful(Some(BoxObjectWithNoSubscribers)), boxId)
      runAcknowledgeScenarioAndAssert(AcknowledgeNotificationsSuccessUpdatedResult(true), repoResult = Future.successful(true))
    }

    "should call confirmations on all ids after ACKNOWLEDGING notifications" in new Setup {
      primeBoxRepo(Future.successful(Some(BoxObjectWithNoSubscribers)), boxId)
      when(mockNotificationsRepo.acknowledgeNotifications(*[BoxId], *)(*)).thenReturn(Future.successful(true))

      private val notificationId: NotificationId = NotificationId.random
      private val notificationId2: NotificationId = NotificationId.random
      await(serviceToTest.acknowledgeNotifications(boxId, clientId, AcknowledgeNotificationsRequest(List(notificationId, notificationId2))))
      verify(mockConfirmationService).handleConfirmation(notificationId)
      verify(mockConfirmationService).handleConfirmation(notificationId2)
    }

    "return AcknowledgeNotificationsSuccessUpdatedResult when repo returns false" in new Setup {
      primeBoxRepo(Future.successful(Some(BoxObjectWithNoSubscribers)), boxId)
      runAcknowledgeScenarioAndAssert(AcknowledgeNotificationsSuccessUpdatedResult(false))
    }

    "return AcknowledgeNotificationsServiceBoxNotFoundResult when box not found" in new Setup {
      primeBoxRepo(Future.successful(None), boxId)
      runAcknowledgeScenarioAndAssert(AcknowledgeNotificationsServiceBoxNotFoundResult(s"BoxId: BoxId(${boxId.value.toString}) not found"))
    }

    "return AcknowledgeNotificationsServiceUnauthorisedResult when caller is not owner of box" in new Setup {
      primeBoxRepo(Future.successful(Some(BoxObjectWithNoSubscribers)), boxId)
      runAcknowledgeScenarioAndAssert(AcknowledgeNotificationsServiceUnauthorisedResult("clientId does not match boxCreator"), ClientId("notTheCLientID"))
    }
  }

  def runAcknowledgeScenarioAndAssert(
      expectedResult: AcknowledgeNotificationsServiceResult,
      clientId: ClientId = clientId,
      repoResult: Future[Boolean] = Future.successful(false)
    ): Unit = {
    when(mockNotificationsRepo.acknowledgeNotifications(*[BoxId], *)(*)).thenReturn(repoResult)

    val result: AcknowledgeNotificationsServiceResult =
      await(serviceToTest.acknowledgeNotifications(boxId, clientId, AcknowledgeNotificationsRequest(List(NotificationId.random))))

    result shouldBe expectedResult
  }
}

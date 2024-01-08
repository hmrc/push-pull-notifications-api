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

import java.time.{Duration, Instant}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.mockito.captor.Captor

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.mocks.repository.{BoxRepositoryMockModule, NotificationsRepositoryMockModule}
import uk.gov.hmrc.pushpullnotificationsapi.mocks.{ConfirmationServiceMockModule, NotificationPushServiceMockModule}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, Notification, NotificationId}
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

class NotificationsServiceSpec extends AsyncHmrcSpec with TestData {

  trait Setup extends BoxRepositoryMockModule with NotificationPushServiceMockModule with NotificationsRepositoryMockModule with ConfirmationServiceMockModule {

    val serviceToTest = new NotificationsService(BoxRepositoryMock.aMock, NotificationsRepositoryMock.aMock, NotificationPushServiceMock.aMock, ConfirmationServiceMock.aMock)

    // API-4417: Default the number of notifications
    NotificationsRepositoryMock.NumberOfNotificationsToReturn.thenReturn(100)

    def primeNotificationRepoSave(result: Option[NotificationId]) = {
      NotificationsRepositoryMock.SaveNotification.thenSucceedsWith(result)
    }

    def primeNotificationRepoGetNotifications(result: List[Notification]) = {
      NotificationsRepositoryMock.GetByBoxIdAndFilters.succeedsWith(boxId, result)
    }

    def primeBoxRepo(result: Option[Box], boxId: BoxId) = {
      BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, result)
    }

    def validateNotificationSaved(notificationCaptor: Captor[Notification]): Unit = {
      NotificationsRepositoryMock.SaveNotification.thenSucceedsWith(Some(notificationId))

      notificationCaptor.value.boxId shouldBe boxId
      notificationCaptor.value.messageContentType shouldBe messageContentTypeJson
      notificationCaptor.value.message shouldBe message
    }

    def runAcknowledgeScenarioAndAssert(
        expectedResult: AcknowledgeNotificationsServiceResult,
        clientId: ClientId = clientId,
        repoResult: Future[Boolean] = Future.successful(false)
      ): Unit = {
      when(NotificationsRepositoryMock.aMock.acknowledgeNotifications(*[BoxId], *)).thenReturn(repoResult)

      val result: AcknowledgeNotificationsServiceResult =
        await(serviceToTest.acknowledgeNotifications(boxId, clientId, AcknowledgeNotificationsRequest(List(NotificationId.random))))

      result shouldBe expectedResult
    }

    implicit val hc: HeaderCarrier = HeaderCarrier()

  }

  "SaveNotification" should {
    "return NotificationCreateSuccessResult when box exists , push is called with subscriber & notification successfully saved" in new Setup {
      primeBoxRepo(Some(BoxObjectWithPushSubscribers), boxId)
      primeNotificationRepoSave(Some(NotificationId.random))
      when(NotificationPushServiceMock.aMock.handlePushNotification(eqTo(BoxObjectWithPushSubscribers), *)(*, *))
        .thenReturn(Future.successful(true))
      val result: NotificationCreateServiceResult = await(serviceToTest.saveNotification(boxId, NotificationId.random, messageContentTypeJson, message))
      result shouldBe NotificationCreateSuccessResult()

      verify(BoxRepositoryMock.aMock, times(1)).findByBoxId(eqTo(boxId))
      verify(NotificationPushServiceMock.aMock).handlePushNotification(eqTo(BoxObjectWithPushSubscribers), *)(*, *)
      NotificationsRepositoryMock.SaveNotification.verifyCalled()
    }

    "return NotificationCreateSuccessResult when box exists, push is called with empty subscriber & notification successfully saved" in new Setup {
      primeBoxRepo(Some(BoxObjectWithNoSubscribers), boxId)
      primeNotificationRepoSave(Some(NotificationId.random))

      val result: NotificationCreateServiceResult = await(serviceToTest.saveNotification(boxId, NotificationId.random, messageContentTypeJson, message))
      result shouldBe NotificationCreateSuccessResult()

      verify(BoxRepositoryMock.aMock, times(1)).findByBoxId(eqTo(boxId))
      verify(NotificationPushServiceMock.aMock).handlePushNotification(eqTo(BoxObjectWithNoSubscribers), *)(*, *)
      NotificationsRepositoryMock.SaveNotification.verifyCalled()
    }

    "return NotificationCreateFailedBoxNotFoundResult when box does not exist" in new Setup {
      primeBoxRepo(None, boxId)

      val result: NotificationCreateServiceResult =
        await(serviceToTest.saveNotification(boxId, NotificationId.random, messageContentTypeXml, message))
      result shouldBe NotificationCreateFailedBoxIdNotFoundResult(s"BoxId: ${boxId} not found")

      verify(BoxRepositoryMock.aMock, times(1)).findByBoxId(eqTo(boxId))
      NotificationsRepositoryMock.verifyZeroInteractions()
    }

  }

  "getNotifications" should {

    val fromDate = Some(Instant.now.minus(Duration.ofHours(2)))
    val toDate = Some(Instant.now)

    "return list of matched notifications" in new Setup {

      primeBoxRepo(Some(BoxObjectWithNoSubscribers), boxId)
      primeNotificationRepoGetNotifications(
        List(Notification(NotificationId.random, boxId, MessageContentType.APPLICATION_JSON, "{}", pendingNotificationStatus, toDate.head))
      )
      val result: Either[GetNotificationsServiceFailedResult, List[Notification]] =
        await(serviceToTest.getNotifications(boxId = boxId, clientId = clientId, status = Some(pendingNotificationStatus), fromDateTime = fromDate, toDateTime = toDate))

      result match {
        case Right(g: List[Notification]) => g.size shouldBe 1
        case _                            => fail()
      }

      NotificationsRepositoryMock.GetByBoxIdAndFilters.verifyCalled()

    }

    "return empty list when no notifications found" in new Setup {

      primeBoxRepo(Some(BoxObjectWithNoSubscribers), boxId)
      primeNotificationRepoGetNotifications(List.empty)

      val result: Either[GetNotificationsServiceFailedResult, List[Notification]] =
        await(serviceToTest.getNotifications(boxId = boxId, clientId = clientId, status = Some(pendingNotificationStatus), fromDateTime = fromDate, toDateTime = toDate))

      result shouldBe Right(List.empty)

      NotificationsRepositoryMock.GetByBoxIdAndFilters.verifyCalled()
    }

    "return notfound exception when client id is different from box creator client id" in new Setup {

      primeBoxRepo(Some(BoxObjectWithNoSubscribers), boxId)

      val result: Either[GetNotificationsServiceFailedResult, List[Notification]] =
        await(serviceToTest.getNotifications(
          boxId = boxId,
          clientId = ClientId(UUID.randomUUID().toString),
          status = Some(pendingNotificationStatus),
          fromDateTime = fromDate,
          toDateTime = toDate
        ))

      result shouldBe Left(GetNotificationsServiceUnauthorisedResult("clientId does not match boxCreator"))

      NotificationsRepositoryMock.verifyZeroInteractions()
    }
  }

  "Acknowledge notifications" should {
    "return AcknowledgeNotificationsSuccessUpdatedResult when repo returns true" in new Setup {
      primeBoxRepo(Some(BoxObjectWithNoSubscribers), boxId)
      runAcknowledgeScenarioAndAssert(AcknowledgeNotificationsSuccessUpdatedResult(true), repoResult = Future.successful(true))
    }

    "should call confirmations on all ids after ACKNOWLEDGING notifications" in new Setup {
      primeBoxRepo(Some(BoxObjectWithNoSubscribers), boxId)
      NotificationsRepositoryMock.AcknowledgeNotifications.succeeds()

      private val notificationId: NotificationId = NotificationId.random
      private val notificationId2: NotificationId = NotificationId.random
      await(serviceToTest.acknowledgeNotifications(boxId, clientId, AcknowledgeNotificationsRequest(List(notificationId, notificationId2))))
      ConfirmationServiceMock.HandleConfirmation.verifyCalledWith(notificationId)
      ConfirmationServiceMock.HandleConfirmation.verifyCalledWith(notificationId2)
    }

    "return AcknowledgeNotificationsSuccessUpdatedResult when repo returns false" in new Setup {
      primeBoxRepo(Some(BoxObjectWithNoSubscribers), boxId)
      runAcknowledgeScenarioAndAssert(AcknowledgeNotificationsSuccessUpdatedResult(false))
    }

    "return AcknowledgeNotificationsServiceBoxNotFoundResult when box not found" in new Setup {
      primeBoxRepo(None, boxId)
      runAcknowledgeScenarioAndAssert(AcknowledgeNotificationsServiceBoxNotFoundResult(s"BoxId: ${boxId} not found"))
    }

    "return AcknowledgeNotificationsServiceUnauthorisedResult when caller is not owner of box" in new Setup {
      primeBoxRepo(Some(BoxObjectWithNoSubscribers), boxId)
      runAcknowledgeScenarioAndAssert(AcknowledgeNotificationsServiceUnauthorisedResult("clientId does not match boxCreator"), ClientId("notTheCLientID"))
    }
  }

}

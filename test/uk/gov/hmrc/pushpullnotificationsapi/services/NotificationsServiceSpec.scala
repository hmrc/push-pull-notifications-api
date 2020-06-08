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
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationContentType, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.{NotificationsRepository, TopicsRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class NotificationsServiceSpec extends UnitSpec with MockitoSugar with ArgumentMatchersSugar with  BeforeAndAfterEach {

  private val mockTopicRepo = mock[TopicsRepository]
  private val mockNotificationsRepo = mock[NotificationsRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTopicRepo, mockNotificationsRepo)
  }

  trait Setup {

    val serviceToTest = new NotificationsService(mockTopicRepo, mockNotificationsRepo)
    val notificationCaptor: Captor[Notification] = ArgCaptor[Notification]

    def primeNotificationRepoSave(result: Future[Option[NotificationId]]) = {
      when(mockNotificationsRepo.saveNotification(any[Notification])(any[ExecutionContext])).thenReturn(result)
    }
    def primeNotificationRepoGetNotifications(result: Future[List[Notification]]): ScalaOngoingStubbing[Future[List[Notification]]] = {
      when(mockNotificationsRepo.getByTopicIdAndFilters(eqTo(topicId),
        any[Option[NotificationStatus]],
        any[Option[DateTime]],
        any[Option[DateTime]])(any[ExecutionContext])).thenReturn(result)
    }


    def primeTopicsRepo(result: Future[List[Topic]], topicId: TopicId): ScalaOngoingStubbing[Future[List[Topic]]] = {
      when(mockTopicRepo.findByTopicId(eqTo(topicId))(any[ExecutionContext])).thenReturn(result)
    }
  }

  private val topicIdStr = "ea69654b-9041-42c9-be5c-68dc11ecbcdf"
  private val topicId = models.TopicId(UUID.fromString(topicIdStr))
  private val clientIdStr = "b15b81ff-536b-4292-ae84-9466af9f3ab1"
  private val clientId = ClientId(clientIdStr)
  private val notificationContentTypeJson = NotificationContentType.APPLICATION_JSON
  private val notificationContentTypeXml = NotificationContentType.APPLICATION_XML
  private val message = "message"
  private val topicObject = Topic(topicId, "topicName", TopicCreator(clientId))

  //TODO -> topic Id (on URl) check topic exists...

  // return any repo errors

  "SaveNotification" should {
    "return true when topic exists & notification successfully saved" in new Setup {
      primeTopicsRepo(Future.successful(List(topicObject)), topicId)
      primeNotificationRepoSave(Future.successful(Some(NotificationId(UUID.randomUUID()))))

      val result: Either[NotificationsServiceFailedResult, NotificationsServiceSuccessResult] = await(serviceToTest.saveNotification(topicId, NotificationId(UUID.randomUUID()), notificationContentTypeJson, message))
      result.right.get shouldBe SaveNotificationSuccessResult()

      verify(mockTopicRepo, times(1)).findByTopicId(eqTo(topicId))(any[ExecutionContext])
      verify(mockNotificationsRepo).saveNotification(notificationCaptor.capture)(any[ExecutionContext])
      notificationCaptor.value.topicId shouldBe topicId
      notificationCaptor.value.notificationContentType shouldBe notificationContentTypeJson
      notificationCaptor.value.message shouldBe message
    }

    "return false when topic does not exist" in new Setup {
      primeTopicsRepo(Future.successful(List.empty), topicId)

      val result: Either[NotificationsServiceFailedResult, NotificationsServiceSuccessResult] =
        await(serviceToTest.saveNotification(topicId, NotificationId(UUID.randomUUID()), notificationContentTypeXml, message))
      result shouldBe Left(NotificationsServiceTopicNotFoundResult((s"Topic Id: TopicId(${topicIdStr}) not found")))

      verify(mockTopicRepo, times(1)).findByTopicId(eqTo(topicId))(any[ExecutionContext])
      verifyZeroInteractions(mockNotificationsRepo)
    }
  }

  "getNotifications" should {

    val status = Some(NotificationStatus.RECEIVED)
    val fromDate = Some(DateTime.now().minusHours(2))
    val toDate = Some(DateTime.now())


    "return list of matched notifications" in new Setup {

      primeTopicsRepo(Future.successful(List(topicObject)), topicId)
      primeNotificationRepoGetNotifications(
        Future.successful(List(Notification(NotificationId(UUID.randomUUID()), topicId,NotificationContentType.APPLICATION_JSON, "{}", status.head, toDate.head)))
      )
     val result: Either[NotificationsServiceFailedResult, NotificationsServiceSuccessResult] =  await(serviceToTest.getNotifications(topicId= topicId,
        clientId = clientId,
        status = status,
        fromDateTime = fromDate,
        toDateTime = toDate))

      val resultsList : GetNotificationsSuccessRetrievedResult = result.right.get.asInstanceOf[GetNotificationsSuccessRetrievedResult]
      resultsList.notifications.isEmpty shouldBe false

      verify(mockNotificationsRepo).getByTopicIdAndFilters(eqTo(topicId), eqTo(status), eqTo(fromDate), eqTo(toDate))(any[ExecutionContext])

    }

    "return empty list when no notifications found" in new Setup {

      primeTopicsRepo(Future.successful(List(topicObject)), topicId)
      primeNotificationRepoGetNotifications(Future.successful(List.empty))

      val result: Either[NotificationsServiceFailedResult, NotificationsServiceSuccessResult] =  await(serviceToTest.getNotifications(topicId= topicId,
        clientId = clientId,
        status = status,
        fromDateTime = fromDate,
        toDateTime = toDate))

      result shouldBe Right(GetNotificationsSuccessRetrievedResult(List.empty))

      verify(mockNotificationsRepo).getByTopicIdAndFilters(eqTo(topicId), eqTo(status), eqTo(fromDate), eqTo(toDate))(any[ExecutionContext])

    }

    "return notfound exception when client id is different from topic creator client id" in new Setup {

      primeTopicsRepo(Future.successful(List(topicObject)), topicId)
      primeNotificationRepoGetNotifications(Future.successful(List.empty))

      val result: Either[NotificationsServiceFailedResult, NotificationsServiceSuccessResult] = await(serviceToTest.getNotifications(topicId = topicId,
          clientId = ClientId(UUID.randomUUID().toString),
          status = status,
          fromDateTime = fromDate,
          toDateTime = toDate))

      result shouldBe Left(NotificationsServiceUnauthorisedResult("clientId does not match topicCreator"))


      verifyNoMoreInteractions(mockNotificationsRepo)

    }
  }
}

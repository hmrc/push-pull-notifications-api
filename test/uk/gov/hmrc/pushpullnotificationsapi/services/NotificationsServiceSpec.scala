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

import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.captor.{ArgCaptor, Captor}
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.{Topic, TopicCreator, TopicNotFoundException}
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationContentType}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{NotificationsRepository, TopicsRepository}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global


class NotificationsServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val mockTopicRepo = mock[TopicsRepository]
  private val mockNotificationsRepo = mock[NotificationsRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTopicRepo, mockNotificationsRepo)
  }

  trait Setup {

    val serviceToTest = new NotificationsService(mockTopicRepo, mockNotificationsRepo)
    val notificationCaptor: Captor[Notification] = ArgCaptor[Notification]

    def primeNotificationRepo(result: Future[Unit]) = {
      when(mockNotificationsRepo.saveNotification(any[Notification])(any[ExecutionContext])).thenReturn(result)
    }

    def primeTopicsRepo(result: Future[List[Topic]], topicId: String) = {
      when(mockTopicRepo.findByTopicId(eqTo(topicId))(any[ExecutionContext])).thenReturn(result)
    }
  }

  private val topicId = "topicId"
  private val notificationContentTypeJson = NotificationContentType.APPLICATION_JSON
  private val notificationContentTypeXml = NotificationContentType.APPLICATION_XML
  private val message = "message"
  private val topicObject = Topic("topicId", "topicName", TopicCreator("clientId"))

  //TODO -> topic Id (on URl) check topic exists...

  // return any repo errors

  "SaveNotification" should {
    "return true when topic exists & notification successfully saved" in new Setup {

      primeTopicsRepo(Future.successful(List(topicObject)), topicId)
      primeNotificationRepo(Future.successful(()))

      val result = await(serviceToTest.saveNotification(topicId, UUID.randomUUID(), notificationContentTypeJson, message))
      result shouldBe true

      verify(mockTopicRepo, times(1)).findByTopicId(eqTo(topicId))(any[ExecutionContext])
      verify(mockNotificationsRepo).saveNotification(notificationCaptor.capture)(any[ExecutionContext])
      notificationCaptor.value.topicId shouldBe topicId
      notificationCaptor.value.notificationContentType shouldBe notificationContentTypeJson
      notificationCaptor.value.message shouldBe message
    }

    "return false when topic does not exist" in new Setup {

      primeTopicsRepo(Future.successful(List.empty), topicId)
      intercept[TopicNotFoundException] {
        await(serviceToTest.saveNotification(topicId, UUID.randomUUID(), notificationContentTypeXml, message))
      }

      verify(mockTopicRepo, times(1)).findByTopicId(eqTo(topicId))(any[ExecutionContext])
      verifyZeroInteractions(mockNotificationsRepo)
    }
  }
}

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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import org.mockito.captor.{ArgCaptor, Captor}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.API_PUSH_SUBSCRIBER
import uk.gov.hmrc.pushpullnotificationsapi.models.{PushSubscriber, SubscriberContainer, SubscribersRequest, SubscriptionType, Topic, TopicCreator, UpdateSubscribersRequest}
import uk.gov.hmrc.pushpullnotificationsapi.repository.TopicsRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class TopicsServiceSpec extends UnitSpec with MockitoSugar {

  private val topicId = UUID.randomUUID().toString
  private val clientId: String = "clientId"
  private val topicName: String = "topicName"
  val endpoint = "/iam/a/callbackurl"
  val subscriberId = UUID.randomUUID().toString
  val updateSubscribersRequestWithId = UpdateSubscribersRequest(List(SubscribersRequest(callBackUrl = endpoint,
    subscriberType = API_PUSH_SUBSCRIBER,
    subscriberId = Some(subscriberId))))

  val updateSubscribersRequestWithOutId = UpdateSubscribersRequest(List(SubscribersRequest(callBackUrl = endpoint,
    subscriberType = API_PUSH_SUBSCRIBER)))

  trait Setup {
    val mockRepository: TopicsRepository = mock[TopicsRepository]
    val objInTest = new TopicsService(mockRepository)
    val topic = Topic(UUID.randomUUID().toString, topicName, TopicCreator(clientId))
    val argumentCaptor: Captor[Topic] = ArgCaptor[Topic]
      when(mockRepository.createTopic(any[Topic])(any[ExecutionContext])).thenReturn(Future.successful(()))
    def getByTopicNameAndClientIdReturns(returnList : List[Topic]): Unit ={
      when(mockRepository.getTopicByNameAndClientId(eqTo(topicName), eqTo(clientId))(any[ExecutionContext]))
        .thenReturn(Future.successful(returnList))
    }

    when(mockRepository.updateSubscribers(eqTo(topicId), any[List[SubscriberContainer[PushSubscriber]]])(any[ExecutionContext]))
      .thenReturn(Future.successful(None))

  }

  "TopicsService" when {

    "createTopic" should {

      "return Created when topic repo returns true" in new Setup {
        await(objInTest.createTopic(topicId, clientId, topicName))


        verify(mockRepository).createTopic(argumentCaptor.capture)(any[ExecutionContext])
        validateTopic(argumentCaptor.value)
      }
    }

    "getByTopicNameAndClientId" should {
      "return list with one topic when topic exists" in new Setup {
        getByTopicNameAndClientIdReturns(List(topic))
        val results = await(objInTest.getTopicByNameAndClientId(topicName, clientId))

        results.size shouldBe 1
      }

      "return empty list when topic does not exists" in new Setup {
        getByTopicNameAndClientIdReturns(List.empty)
        val results = await(objInTest.getTopicByNameAndClientId(topicName, clientId))

        results.size shouldBe 0
      }
    }

    "updateSubscribers" should {

      "pass the correct values to the repository and not generate a new subscriberId when a subscriberId is in the request" in new Setup {

        await(objInTest.updateSubscribers(topicId,updateSubscribersRequestWithId))

        val subscriberCaptor: Captor[List[SubscriberContainer[PushSubscriber]]] = ArgCaptor[List[SubscriberContainer[PushSubscriber]]]
        verify(mockRepository).updateSubscribers(eqTo(topicId), subscriberCaptor.capture)(any[ExecutionContext])

        val capturedSubscriber = subscriberCaptor.value.head.elem
        capturedSubscriber.callBackUrl shouldBe endpoint
        capturedSubscriber.subscriberId shouldBe subscriberId
        capturedSubscriber.subscriptionType shouldBe API_PUSH_SUBSCRIBER

      }

      "pass the correct values to the repository and should generate a new subscriberId when a subscriberId is not in the request" in new Setup {

        await(objInTest.updateSubscribers(topicId,updateSubscribersRequestWithOutId))

        val subscriberCaptor: Captor[List[SubscriberContainer[PushSubscriber]]] = ArgCaptor[List[SubscriberContainer[PushSubscriber]]]
        verify(mockRepository).updateSubscribers(eqTo(topicId), subscriberCaptor.capture)(any[ExecutionContext])

        val capturedSubscriber = subscriberCaptor.value.head.elem
        capturedSubscriber.callBackUrl shouldBe endpoint
        capturedSubscriber.subscriberId shouldNot be(subscriberId)
        capturedSubscriber.subscriptionType shouldBe API_PUSH_SUBSCRIBER

      }

    }

  }

  def validateTopic(topic: Topic): Unit = {
    topic.topicId shouldBe topicId
    topic.topicName shouldBe topicName
    topic.subscribers.size shouldBe 0
    topic.topicCreator.clientId shouldBe clientId
  }
}
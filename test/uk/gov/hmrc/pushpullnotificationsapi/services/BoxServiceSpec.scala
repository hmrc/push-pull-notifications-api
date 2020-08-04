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

import org.mockito.ArgumentMatchersSugar
import org.mockito.Mockito.{reset, verify, when}
import org.mockito.captor.{ArgCaptor, Captor}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.{API_PULL_SUBSCRIBER, API_PUSH_SUBSCRIBER}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class BoxServiceSpec extends UnitSpec with MockitoSugar with ArgumentMatchersSugar {

  val mockRepository: BoxRepository = mock[BoxRepository]
  private val boxIdUUID = UUID.randomUUID()
  private val boxId = BoxId(boxIdUUID)
  private val clientIDUUID = UUID.randomUUID().toString
  private val clientId: ClientId = ClientId(clientIDUUID)
  private val boxName: String = "boxName"
  val endpoint = "/iam/a/callbackurl"
  def updateSubscribersRequestWithId(subtype: SubscriptionType): UpdateSubscriberRequest =
    UpdateSubscriberRequest(SubscriberRequest(callBackUrl = endpoint, subscriberType = subtype))

  val updateSubscribersRequestWithOutId: UpdateSubscriberRequest =
    UpdateSubscriberRequest(SubscriberRequest(callBackUrl = endpoint, subscriberType = API_PUSH_SUBSCRIBER))

  trait Setup {
    reset(mockRepository)
    val objInTest = new BoxService(mockRepository)
    val box: Box = Box(boxId, boxName, BoxCreator(clientId))
    val argumentCaptor: Captor[Box] = ArgCaptor[Box]

    when(mockRepository.createBox(any[Box])(any[ExecutionContext])).thenReturn(Future.successful(Some(boxId)))

    def getByBoxNameAndClientIdReturns(returnList: List[Box]): Unit = {
     when(mockRepository.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(any[ExecutionContext]))
        .thenReturn(Future.successful(returnList))
    }

    when(mockRepository.updateSubscriber(eqTo(boxId), any[SubscriberContainer[PushSubscriber]])(any[ExecutionContext])).thenReturn(Future.successful(None))

  }

  "BoxService" when {

    "createBox" should {

      "return Created when box repo returns true" in new Setup {
        await(objInTest.createBox(boxId, clientId, boxName))


        verify(mockRepository).createBox(argumentCaptor.capture)(any[ExecutionContext])
        validateBox(argumentCaptor.value)
      }
    }

    "getByBoxNameAndClientId" should {
      "return list with one box when box exists" in new Setup {
        getByBoxNameAndClientIdReturns(List(box))
        val results: immutable.Seq[Box] = await(objInTest.getBoxByNameAndClientId(boxName, clientId))

        results.size shouldBe 1
      }

      "return empty list when box does not exists" in new Setup {
        getByBoxNameAndClientIdReturns(List.empty)
        val results: immutable.Seq[Box] = await(objInTest.getBoxByNameAndClientId(boxName, clientId))

        results.size shouldBe 0
      }
    }

    "updateSubscribers" should {

      "correctly update box with a PUSH SUBSCRIBER" in new Setup {

        await(objInTest.updateSubscriber(boxId, updateSubscribersRequestWithId(API_PUSH_SUBSCRIBER)))

        val subscriberCaptor: Captor[SubscriberContainer[PushSubscriber]] = ArgCaptor[SubscriberContainer[PushSubscriber]]
        verify(mockRepository).updateSubscriber(eqTo(boxId), subscriberCaptor.capture)(any[ExecutionContext])

        val capturedSubscriber: PushSubscriber = subscriberCaptor.value.elem
        capturedSubscriber.callBackUrl shouldBe endpoint
        capturedSubscriber.subscriptionType shouldBe API_PUSH_SUBSCRIBER

      }

      "correctly update box with a PULL SUBSCRIBER" in new Setup {

        await(objInTest.updateSubscriber(boxId, updateSubscribersRequestWithId(API_PULL_SUBSCRIBER)))

        val subscriberCaptor: Captor[SubscriberContainer[PullSubscriber]] = ArgCaptor[SubscriberContainer[PullSubscriber]]
        verify(mockRepository).updateSubscriber(eqTo(boxId), subscriberCaptor.capture)(any[ExecutionContext])

        val capturedSubscriber: PullSubscriber = subscriberCaptor.value.elem
        capturedSubscriber.callBackUrl shouldBe endpoint
        capturedSubscriber.subscriptionType shouldBe API_PULL_SUBSCRIBER

      }

    }
    "updateCallbackUrl" should {
    
       "return CallbackUrlUpdated when process completes successfully" in new Setup {
          val validRequest: AddCallbackUrlRequest = AddCallbackUrlRequest(ClientId("someID"), "callbackUrl", "token")
          val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(BoxId(UUID.randomUUID()), validRequest))
          result.isInstanceOf[CallbackUrlUpdated] shouldBe true
       }
    }

  }

  def validateBox(box: Box): Unit = {
    box.boxId shouldBe boxId
    box.boxName shouldBe boxName
    box.subscriber.isDefined shouldBe false
    box.boxCreator.clientId shouldBe clientId
  }
}

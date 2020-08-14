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
import org.mockito.Mockito.{reset, verify, verifyNoInteractions, when}
import org.mockito.captor.{ArgCaptor, Captor}
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.connectors.PushConnector
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.{API_PULL_SUBSCRIBER, API_PUSH_SUBSCRIBER}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class BoxServiceSpec extends UnitSpec with MockitoSugar with ArgumentMatchersSugar {

  val mockRepository: BoxRepository = mock[BoxRepository]
  val mockConnector: PushConnector = mock[PushConnector]
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
    reset(mockRepository, mockConnector)
    val objInTest = new BoxService(mockRepository, mockConnector)
    val box: Box = Box(boxId, boxName, BoxCreator(clientId))
    val argumentCaptor: Captor[Box] = ArgCaptor[Box]

    when(mockRepository.createBox(any[Box])(any[ExecutionContext])).thenReturn(Future.successful(Some(boxId)))

    def getByBoxNameAndClientIdReturns(optionalBox: Option[Box]): OngoingStubbing[Future[Option[Box]]] =
     when(mockRepository.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(any[ExecutionContext])).thenReturn(Future.successful(optionalBox))

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
        getByBoxNameAndClientIdReturns(Some(box))
        val result: Option[Box] = await(objInTest.getBoxByNameAndClientId(boxName, clientId))

        result shouldBe Some(box)
      }

      "return empty list when box does not exists" in new Setup {
        getByBoxNameAndClientIdReturns(None)

        val result: Option[Box] = await(objInTest.getBoxByNameAndClientId(boxName, clientId))

        result shouldBe None
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
         when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(Some(box)))
        when(mockRepository.updateSubscriber(eqTo(boxId), any[SubscriberContainer[PushSubscriber]])(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(box)))

          val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
         when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorSuccessResult()))

          val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
          result.isInstanceOf[CallbackUrlUpdated] shouldBe true
         verify(mockConnector).validateCallbackUrl(eqTo(validRequest))
       }

      "return CallbackUrlUpdated when callbackUrl is empty and dont call callBackUrl in connector" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(Some(box)))
        when(mockRepository.updateSubscriber(eqTo(boxId), any[SubscriberContainer[PushSubscriber]])(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(box)))

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "")

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        verifyNoInteractions(mockConnector)

        result.isInstanceOf[CallbackUrlUpdated] shouldBe true
      }

      "return UnableToUpdateCallbackUrl when update of box with callback fails" in new Setup {
         when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(Some(box)))
        when(mockRepository.updateSubscriber(eqTo(boxId), any[SubscriberContainer[PushSubscriber]])(any[ExecutionContext]))
        .thenReturn(Future.successful(None))

          val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
         when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorSuccessResult()))

          val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
          result.isInstanceOf[UnableToUpdateCallbackUrl] shouldBe true


         verify(mockConnector).validateCallbackUrl(eqTo(validRequest))
       }

      "return UpdateCallbackUrlUnauthorisedResult when clientId of box is different from request clientId" in new Setup {
         when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(Some(box)))

          val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(ClientId("someotherId"), "callbackUrl")
          val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
          result.isInstanceOf[UpdateCallbackUrlUnauthorisedResult] shouldBe true
          verifyNoInteractions(mockConnector)
       }


      "return CallbackValidationFailed when connector call returns false" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(Some(box)))

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
        when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorFailedResult("")))

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[CallbackValidationFailed] shouldBe true
      }

      "return BoxIdNotFound when boxId is not found" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(None))
        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[BoxIdNotFound] shouldBe true

        verifyNoInteractions(mockConnector)
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

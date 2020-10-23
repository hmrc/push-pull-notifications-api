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
import org.mockito.ArgumentMatchersSugar
import org.mockito.Mockito.{verify, verifyNoInteractions, when}
import org.mockito.MockitoSugar.times
import org.mockito.captor.{ArgCaptor, Captor}
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.connectors.{ApiPlatformEventsConnector, ApplicationResponse, PushConnector, ThirdPartyApplicationConnector}
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.API_PUSH_SUBSCRIBER
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class BoxServiceSpec extends UnitSpec with MockitoSugar with ArgumentMatchersSugar {

  private val boxIdUUID = UUID.randomUUID()
  private val boxId = BoxId(boxIdUUID)
  private val clientIDUUID = UUID.randomUUID().toString
  private val clientId: ClientId = ClientId(clientIDUUID)
  private val clientSecret: ClientSecret = ClientSecret("someRandomSecret")
  private val client: Client = Client(clientId, Seq(clientSecret))
  private val boxName: String = "boxName"

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val endpoint = "/iam/a/callbackurl"

  def updateSubscribersRequestWithId(subtype: SubscriptionType): UpdateSubscriberRequest =
    UpdateSubscriberRequest(SubscriberRequest(callBackUrl = endpoint, subscriberType = subtype))

  val updateSubscribersRequestWithOutId: UpdateSubscriberRequest =
    UpdateSubscriberRequest(SubscriberRequest(callBackUrl = endpoint, subscriberType = API_PUSH_SUBSCRIBER))

  trait Setup {
    val mockRepository: BoxRepository = mock[BoxRepository]
    val mockConnector: PushConnector = mock[PushConnector]
    val mockClientService: ClientService = mock[ClientService]
    val mockThirdPartyApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]
    val mockApiPlatformEventsConnector: ApiPlatformEventsConnector = mock[ApiPlatformEventsConnector]

    val objInTest = new BoxService(mockRepository, mockConnector, mockThirdPartyApplicationConnector, mockApiPlatformEventsConnector, mockClientService)
    val box: Box = Box(boxId, boxName, BoxCreator(clientId))
    val boxWithExistingSubscriber: Box = box.copy(subscriber = Some(PushSubscriber(endpoint, DateTime.now)))
    val argumentCaptor: Captor[Box] = ArgCaptor[Box]


    def getByBoxNameAndClientIdReturns(optionalBox: Option[Box]): OngoingStubbing[Future[Option[Box]]] =
      when(mockRepository.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(any[ExecutionContext])).thenReturn(Future.successful(optionalBox))

    when(mockRepository.updateSubscriber(eqTo(boxId), any[SubscriberContainer[PushSubscriber]])(any[ExecutionContext])).thenReturn(Future.successful(None))
  }

  "BoxService" when {

    "createBox" should {

      val applicationId = ApplicationId("12345")

      "return BoxCreatedResult and call tpa to get application id when box is created" in new Setup {
        when(mockRepository.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(any[ExecutionContext])).thenReturn(Future.successful(None))
        when(mockThirdPartyApplicationConnector.getApplicationDetails(eqTo(clientId))(any[HeaderCarrier]))
          .thenReturn(Future.successful(ApplicationResponse(applicationId)))
        when(mockRepository.createBox(any[Box])(any[ExecutionContext])).thenReturn(Future.successful(BoxCreatedResult(box)))
        when(mockClientService.findOrCreateClient(clientId)).thenReturn(Future.successful(client))

        val result: CreateBoxResult = await(objInTest.createBox(clientId, boxName))

        result.isInstanceOf[BoxCreatedResult] shouldBe true

        verify(mockRepository, times(1)).createBox(argumentCaptor.capture)(any[ExecutionContext])
        verify(mockThirdPartyApplicationConnector, times(1)).getApplicationDetails(eqTo(clientId))(any[HeaderCarrier])
        verify(mockClientService, times(1)).findOrCreateClient(clientId)

        validateBox(argumentCaptor.value, Some(applicationId))
      }


      "return BoxRetrievedResult when box is already exists and verify no attempt to call tpa" in new Setup {
        when(mockRepository.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(any[ExecutionContext])).thenReturn(Future.successful(Some(box)))

        when(mockClientService.findOrCreateClient(clientId)).thenReturn(Future.successful(client))

        val result: CreateBoxResult = await(objInTest.createBox(clientId, boxName))

        result.isInstanceOf[BoxRetrievedResult] shouldBe true

        verify(mockRepository, times(0)).createBox(argumentCaptor.capture)(any[ExecutionContext])
        verify(mockThirdPartyApplicationConnector, times(0)).getApplicationDetails(eqTo(clientId))(any[HeaderCarrier])
        verify(mockClientService, times(0)).findOrCreateClient(clientId)

      }

      "return BoxCreateFailedResult when attempt to get applicationId fails during box creation" in new Setup {
        when(mockRepository.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(any[ExecutionContext])).thenReturn(Future.successful(None))
        when(mockRepository.createBox(any[Box])(any[ExecutionContext])).thenReturn(Future.successful(BoxCreatedResult(box)))

        when(mockClientService.findOrCreateClient(clientId)).thenReturn(Future.successful(client))
        when(mockThirdPartyApplicationConnector.getApplicationDetails(eqTo(clientId))(any[HeaderCarrier])).thenReturn(Future.failed(new RuntimeException("")))


        val result: CreateBoxResult = await(objInTest.createBox(clientId, boxName))
        result.isInstanceOf[BoxCreateFailedResult] shouldBe true


        verify(mockRepository, times(1)).getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(any[ExecutionContext])
        verify(mockRepository, times(0)).createBox(argumentCaptor.capture)(any[ExecutionContext])
        verify(mockThirdPartyApplicationConnector, times(1)).getApplicationDetails(eqTo(clientId))(any[HeaderCarrier])
        verify(mockClientService, times(1)).findOrCreateClient(clientId)

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

    "updateCallbackUrl" should {
      val applicationId = ApplicationId("123124")

      "return CallbackUrlUpdated when process completes successfully" in new Setup {
        val boxWithApplicationId: Box = boxWithExistingSubscriber.copy(applicationId = Some(applicationId))
        val newUrl = "callbackUrl"
        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, newUrl)

        when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(Some(boxWithApplicationId)))
        when(mockRepository.updateSubscriber(eqTo(boxId), any[SubscriberContainer[PushSubscriber]])(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(boxWithApplicationId)))
        when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorSuccessResult()))
        when(mockApiPlatformEventsConnector.sendCallBackUpdatedEvent(eqTo(applicationId), any[String], eqTo(newUrl))(any[HeaderCarrier]))
          .thenReturn(Future.successful(true))

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[CallbackUrlUpdated] shouldBe true

        verifyNoInteractions(mockThirdPartyApplicationConnector)
        verify(mockRepository, times(0)).updateApplicationId(any[BoxId], any[ApplicationId])(any[ExecutionContext])
        verify(mockConnector).validateCallbackUrl(eqTo(validRequest))
        verify(mockApiPlatformEventsConnector).sendCallBackUpdatedEvent(eqTo(applicationId), eqTo(endpoint), eqTo(newUrl))(any[HeaderCarrier])
      }

      "return CallbackUrlUpdated when box has application id added and callback url is validated" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(Some(boxWithExistingSubscriber)))
        when(mockThirdPartyApplicationConnector.getApplicationDetails(eqTo(clientId))(any[HeaderCarrier]))
          .thenReturn(Future.successful(ApplicationResponse(applicationId)))
        when(mockRepository.updateApplicationId(eqTo(boxId), eqTo(applicationId))(any[ExecutionContext]))
          .thenReturn(Future.successful(boxWithExistingSubscriber.copy(applicationId = Some(applicationId))))
        when(mockRepository.updateSubscriber(eqTo(boxId), any[SubscriberContainer[PushSubscriber]])(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(box)))
        when(mockApiPlatformEventsConnector.sendCallBackUpdatedEvent(any[ApplicationId], any[String], any[String])(any[HeaderCarrier])).thenReturn(Future.successful(true))  

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
        when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorSuccessResult()))

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[CallbackUrlUpdated] shouldBe true
        
        verify(mockThirdPartyApplicationConnector, times(1)).getApplicationDetails(eqTo(clientId))(any[HeaderCarrier])
        verify(mockRepository, times(1)).updateApplicationId(any[BoxId], any[ApplicationId])(any[ExecutionContext])
        verify(mockConnector).validateCallbackUrl(eqTo(validRequest))
        verify(mockApiPlatformEventsConnector).sendCallBackUpdatedEvent(any[ApplicationId], any[String], any[String])(any[HeaderCarrier])
      }

      "return CallbackUrlUpdated when callbackUrl is empty, applicationId exists, and dont call callBackUrl in connector" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(Some(box.copy(applicationId = Some(applicationId)))))
        when(mockRepository.updateSubscriber(eqTo(boxId), any[SubscriberContainer[PushSubscriber]])(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(box)))
        when(mockApiPlatformEventsConnector.sendCallBackUpdatedEvent(any[ApplicationId], any[String], any[String])(any[HeaderCarrier])).thenReturn(Future.successful(true))  

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "")

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        verifyNoInteractions(mockConnector)

        result.isInstanceOf[CallbackUrlUpdated] shouldBe true
        verify(mockApiPlatformEventsConnector).sendCallBackUpdatedEvent(any[ApplicationId], any[String], any[String])(any[HeaderCarrier])
      }

      "return UnableToUpdateCallbackUrl when update of box with applicationId with callback fails" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(Some(box.copy(applicationId = Some(applicationId)))))
        when(mockRepository.updateSubscriber(eqTo(boxId), any[SubscriberContainer[PushSubscriber]])(any[ExecutionContext]))
          .thenReturn(Future.successful(None))

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
        when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorSuccessResult()))

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[UnableToUpdateCallbackUrl] shouldBe true


        verify(mockConnector).validateCallbackUrl(eqTo(validRequest))
        verifyNoInteractions(mockApiPlatformEventsConnector)
      }

      "return UnableToUpdateCallbackUrl box has no appliction id and call to tpa fails" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(Some(box)))
        when(mockThirdPartyApplicationConnector.getApplicationDetails(eqTo(clientId))(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException("some Error")))

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
        when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorSuccessResult()))

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[UnableToUpdateCallbackUrl] shouldBe true

        verifyNoInteractions(mockConnector)
        verifyNoInteractions(mockApiPlatformEventsConnector)
      }

      "return UpdateCallbackUrlUnauthorisedResult when clientId of box is different from request clientId" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(Some(box)))

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(ClientId("someotherId"), "callbackUrl")
        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[UpdateCallbackUrlUnauthorisedResult] shouldBe true
        verifyNoInteractions(mockConnector)
        verifyNoInteractions(mockApiPlatformEventsConnector)
      }


      "return CallbackValidationFailed when connector call returns false" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(box.copy(applicationId = Some(applicationId)))))

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
        when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorFailedResult("")))

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[CallbackValidationFailed] shouldBe true
        verifyNoInteractions(mockApiPlatformEventsConnector)
      }

      "return BoxIdNotFound when boxId is not found" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(any[ExecutionContext])).thenReturn(Future.successful(None))
        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[BoxIdNotFound] shouldBe true

        verifyNoInteractions(mockConnector)
        verifyNoInteractions(mockApiPlatformEventsConnector)
      }
    }

  }

  def validateBox(box: Box, expectedApplicationId: Option[ApplicationId]): Unit = {
    box.boxName shouldBe boxName
    box.subscriber.isDefined shouldBe false
    box.boxCreator.clientId shouldBe clientId
    box.applicationId shouldBe expectedApplicationId
  }
}

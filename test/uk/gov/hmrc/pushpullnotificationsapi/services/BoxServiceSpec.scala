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

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.mockito.Mockito.verifyNoInteractions
import org.mockito.captor.ArgCaptor

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.connectors.{ApiPlatformEventsConnector, ApplicationResponse, PushConnector, ThirdPartyApplicationConnector}
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.API_PUSH_SUBSCRIBER
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository

class BoxServiceSpec extends AsyncHmrcSpec {

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
    val boxWithExistingSubscriber: Box = box.copy(subscriber = Some(PushSubscriber(endpoint, Instant.now)))
    val argumentCaptor = ArgCaptor[Box]

    def getByBoxNameAndClientIdReturns(optionalBox: Option[Box]) =
      when(mockRepository.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(*)).thenReturn(Future.successful(optionalBox))

    when(mockRepository.updateSubscriber(eqTo(boxId), *)(*)).thenReturn(Future.successful(None))
  }

  "BoxService" when {
    "createBox" should {
      val applicationId = ApplicationId("12345")

      "return BoxCreatedResult and call tpa to get application id when box is created" in new Setup {
        when(mockRepository.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(*)).thenReturn(Future.successful(None))
        when(mockThirdPartyApplicationConnector.getApplicationDetails(eqTo(clientId))(*))
          .thenReturn(Future.successful(ApplicationResponse(applicationId)))
        when(mockRepository.createBox(*)(*)).thenReturn(Future.successful(BoxCreatedResult(box)))
        when(mockClientService.findOrCreateClient(eqTo(clientId))).thenReturn(Future.successful(client))

        val result: CreateBoxResult = await(objInTest.createBox(clientId, boxName))

        result.isInstanceOf[BoxCreatedResult] shouldBe true

        verify(mockRepository, times(1)).createBox(argumentCaptor)(*)
        verify(mockThirdPartyApplicationConnector, times(1)).getApplicationDetails(eqTo(clientId))(*)
        verify(mockClientService, times(1)).findOrCreateClient(eqTo(clientId))

        validateBox(argumentCaptor.value, Some(applicationId))
      }

      "return BoxRetrievedResult when box is already exists and verify no attempt to call tpa" in new Setup {
        when(mockRepository.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(*)).thenReturn(Future.successful(Some(box)))

        when(mockClientService.findOrCreateClient(eqTo(clientId))).thenReturn(Future.successful(client))

        val result: CreateBoxResult = await(objInTest.createBox(clientId, boxName))

        result.isInstanceOf[BoxRetrievedResult] shouldBe true

        verify(mockRepository, times(0)).createBox(argumentCaptor)(*)
        verify(mockThirdPartyApplicationConnector, times(0)).getApplicationDetails(eqTo(clientId))(*)
        verify(mockClientService, times(0)).findOrCreateClient(eqTo(clientId))

      }

      "return BoxCreateFailedResult when attempt to get applicationId fails during box creation" in new Setup {
        when(mockRepository.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(*)).thenReturn(Future.successful(None))
        when(mockRepository.createBox(*)(*)).thenReturn(Future.successful(BoxCreatedResult(box)))

        when(mockClientService.findOrCreateClient(clientId)).thenReturn(Future.successful(client))
        when(mockThirdPartyApplicationConnector.getApplicationDetails(eqTo(clientId))(*)).thenReturn(Future.failed(new RuntimeException("")))

        val result: CreateBoxResult = await(objInTest.createBox(clientId, boxName))
        result.isInstanceOf[BoxCreateFailedResult] shouldBe true

        verify(mockRepository, times(1)).getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(*)
        verify(mockRepository, times(0)).createBox(argumentCaptor)(*)
        verify(mockThirdPartyApplicationConnector, times(1)).getApplicationDetails(eqTo(clientId))(*)
        verify(mockClientService, times(1)).findOrCreateClient(eqTo(clientId))
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

    "getBoxesByClientId" should {
      "delegate to repo and return same list" in new Setup {
        val boxes: List[Box] = List()
        when(mockRepository.getBoxesByClientId(eqTo(clientId))).thenReturn(Future.successful(boxes))

        val result = await(objInTest.getBoxesByClientId(clientId))

        result should be theSameInstanceAs boxes

        verify(mockRepository, times(1)).getBoxesByClientId(eqTo(clientId))
      }
    }

    "getAllBoxes" in new Setup {
      val boxes: List[Box] = List()
      when(mockRepository.getAllBoxes()(*)).thenReturn(Future.successful(boxes))

      val result = await(objInTest.getAllBoxes())

      result should be theSameInstanceAs boxes

      verify(mockRepository, times(1)).getAllBoxes()(*)
    }

    "updateCallbackUrl" should {
      val applicationId = ApplicationId("123124")

      "return CallbackUrlUpdated when process completes successfully" in new Setup {
        val boxWithApplicationId: Box = boxWithExistingSubscriber.copy(applicationId = Some(applicationId))
        val newUrl = "callbackUrl"
        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, newUrl)

        when(mockRepository.findByBoxId(eqTo(boxId))(*)).thenReturn(Future.successful(Some(boxWithApplicationId)))
        when(mockRepository.updateSubscriber(eqTo(boxId), *)(*))
          .thenReturn(Future.successful(Some(boxWithApplicationId)))
        when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorSuccessResult()))
        when(mockApiPlatformEventsConnector.sendCallBackUpdatedEvent(eqTo(applicationId), *, eqTo(newUrl), eqTo(boxWithApplicationId))(*))
          .thenReturn(Future.successful(true))

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[CallbackUrlUpdated] shouldBe true

        verifyNoInteractions(mockThirdPartyApplicationConnector)
        verify(mockRepository, times(0)).updateApplicationId(*[BoxId], *[ApplicationId])(*)
        verify(mockConnector).validateCallbackUrl(eqTo(validRequest))
        verify(mockApiPlatformEventsConnector).sendCallBackUpdatedEvent(eqTo(applicationId), eqTo(endpoint), eqTo(newUrl), eqTo(boxWithApplicationId))(*)
      }

      "return CallbackUrlUpdated when box has application id added and callback url is validated" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(*)).thenReturn(Future.successful(Some(boxWithExistingSubscriber)))
        when(mockThirdPartyApplicationConnector.getApplicationDetails(eqTo(clientId))(*))
          .thenReturn(Future.successful(ApplicationResponse(applicationId)))
        when(mockRepository.updateApplicationId(eqTo(boxId), eqTo(applicationId))(*))
          .thenReturn(Future.successful(boxWithExistingSubscriber.copy(applicationId = Some(applicationId))))
        when(mockRepository.updateSubscriber(eqTo(boxId), *)(*))
          .thenReturn(Future.successful(Some(box)))
        when(mockApiPlatformEventsConnector.sendCallBackUpdatedEvent(*[ApplicationId], *, *, *)(*)).thenReturn(Future.successful(true))

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
        when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorSuccessResult()))

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[CallbackUrlUpdated] shouldBe true

        verify(mockThirdPartyApplicationConnector, times(1)).getApplicationDetails(eqTo(clientId))(*)
        verify(mockRepository, times(1)).updateApplicationId(*[BoxId], *[ApplicationId])(*)
        verify(mockConnector).validateCallbackUrl(eqTo(validRequest))
        verify(mockApiPlatformEventsConnector).sendCallBackUpdatedEvent(*[ApplicationId], *, *, *)(*)
      }

      "return CallbackUrlUpdated when callbackUrl is empty, applicationId exists, and dont call callBackUrl in connector" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(*)).thenReturn(Future.successful(Some(box.copy(applicationId = Some(applicationId)))))
        when(mockRepository.updateSubscriber(eqTo(boxId), *)(*))
          .thenReturn(Future.successful(Some(box)))
        when(mockApiPlatformEventsConnector.sendCallBackUpdatedEvent(*[ApplicationId], *, *, *)(*)).thenReturn(Future.successful(true))

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "")

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        verifyNoInteractions(mockConnector)

        result.isInstanceOf[CallbackUrlUpdated] shouldBe true
        verify(mockApiPlatformEventsConnector).sendCallBackUpdatedEvent(*[ApplicationId], *, *, *)(*)
      }

      "return UnableToUpdateCallbackUrl when update of box with applicationId with callback fails" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(*)).thenReturn(Future.successful(Some(box.copy(applicationId = Some(applicationId)))))
        when(mockRepository.updateSubscriber(eqTo(boxId), *)(*))
          .thenReturn(Future.successful(None))

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
        when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorSuccessResult()))

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[UnableToUpdateCallbackUrl] shouldBe true

        verify(mockConnector).validateCallbackUrl(eqTo(validRequest))
        verifyNoInteractions(mockApiPlatformEventsConnector)
      }

      "return UnableToUpdateCallbackUrl box has no appliction id and call to tpa fails" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(*)).thenReturn(Future.successful(Some(box)))
        when(mockThirdPartyApplicationConnector.getApplicationDetails(eqTo(clientId))(*))
          .thenReturn(Future.failed(new RuntimeException("some Error")))

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
        when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorSuccessResult()))

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[UnableToUpdateCallbackUrl] shouldBe true

        verifyNoInteractions(mockConnector)
        verifyNoInteractions(mockApiPlatformEventsConnector)
      }

      "return UpdateCallbackUrlUnauthorisedResult when clientId of box is different from request clientId" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(*)).thenReturn(Future.successful(Some(box)))

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(ClientId("someotherId"), "callbackUrl")
        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[UpdateCallbackUrlUnauthorisedResult] shouldBe true
        verifyNoInteractions(mockConnector)
        verifyNoInteractions(mockApiPlatformEventsConnector)
      }

      "return CallbackValidationFailed when connector call returns false" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(*))
          .thenReturn(Future.successful(Some(box.copy(applicationId = Some(applicationId)))))

        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
        when(mockConnector.validateCallbackUrl(eqTo(validRequest))).thenReturn(Future.successful(PushConnectorFailedResult("")))

        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[CallbackValidationFailed] shouldBe true
        verifyNoInteractions(mockApiPlatformEventsConnector)
      }

      "return BoxIdNotFound when boxId is not found" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(*)).thenReturn(Future.successful(None))
        val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
        val result: UpdateCallbackUrlResult = await(objInTest.updateCallbackUrl(boxId, validRequest))
        result.isInstanceOf[BoxIdNotFound] shouldBe true

        verifyNoInteractions(mockConnector)
        verifyNoInteractions(mockApiPlatformEventsConnector)
      }
    }

    "validateBoxOwner" should {

      "return ValidateBoxOwnerSuccessResult when boxId is found and clientId matches" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(*)).thenReturn(Future.successful(Some(box)))
        val result: ValidateBoxOwnerResult = await(objInTest.validateBoxOwner(boxId, clientId))
        result.isInstanceOf[ValidateBoxOwnerSuccessResult] shouldBe true
      }

      "return ValidateBoxOwnerFailedResult when boxId is found and clientId doesn't match" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(*)).thenReturn(Future.successful(Some(box)))
        val result: ValidateBoxOwnerResult = await(objInTest.validateBoxOwner(boxId, ClientId(UUID.randomUUID().toString)))
        result.isInstanceOf[ValidateBoxOwnerFailedResult] shouldBe true
      }

      "return ValidateBoxOwnerNotFoundResult when boxId is not found" in new Setup {
        when(mockRepository.findByBoxId(eqTo(boxId))(*)).thenReturn(Future.successful(None))
        val result: ValidateBoxOwnerResult = await(objInTest.validateBoxOwner(boxId, clientId))
        result.isInstanceOf[ValidateBoxOwnerNotFoundResult] shouldBe true
      }
    }
  }

  "deleteBox" should {

    "return BoxDeleteSuccessfulResult when a box is found by boxId" in new Setup {
      val clientManagedBox: Box = box.copy(clientManaged = true)
      when(mockRepository.findByBoxId(eqTo(clientManagedBox.boxId))(*)).thenReturn(Future.successful(Some(clientManagedBox)))
      when(mockRepository.deleteBox(eqTo(clientManagedBox.boxId))(*)).thenReturn(Future(BoxDeleteSuccessfulResult()))

      val result: DeleteBoxResult = await(objInTest.deleteBox(clientManagedBox.boxCreator.clientId, clientManagedBox.boxId))
      result shouldBe BoxDeleteSuccessfulResult()
    }

    "return BoxDeleteAccessDeniedResult when clientManaged is false" in new Setup {
      when(mockRepository.findByBoxId(eqTo(boxId))(*)).thenReturn(Future.successful(Some(box)))
      when(mockRepository.deleteBox(eqTo(box.boxId))(*)).thenReturn(Future(BoxDeleteAccessDeniedResult()))

      val result: DeleteBoxResult = await(objInTest.deleteBox(clientId, boxId))
      result shouldBe BoxDeleteAccessDeniedResult()
    }

    "return BoxDeleteAccessDeniedResult when the given clientId does not match the box's clientId" in new Setup {
      val incorrectClientId: ClientId = ClientId(UUID.randomUUID().toString)
      val clientManagedBox: Box = box.copy(clientManaged = true)

      when(mockRepository.findByBoxId(eqTo(boxId))(*)).thenReturn(Future.successful(Some(clientManagedBox)))

      val result: DeleteBoxResult = await(objInTest.deleteBox(incorrectClientId, boxId))
      result shouldBe BoxDeleteAccessDeniedResult()
    }
  }

  def validateBox(box: Box, expectedApplicationId: Option[ApplicationId]): Unit = {
    box.boxName shouldBe boxName
    box.subscriber.isDefined shouldBe false
    box.boxCreator.clientId shouldBe clientId
    box.applicationId shouldBe expectedApplicationId
  }
}

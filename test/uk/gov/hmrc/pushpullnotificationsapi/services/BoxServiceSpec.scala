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
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.connectors.{ApiPlatformEventsConnector, ApplicationResponse, PushConnector, ThirdPartyApplicationConnector}
import uk.gov.hmrc.pushpullnotificationsapi.mocks.{BoxRepositoryMockModule, ClientServiceMockModule}
import uk.gov.hmrc.pushpullnotificationsapi.mocks.connectors.{ApiPlatformEventsConnectorMockModule, PushConnectorMockModule, ThirdPartyApplicationConnectorMockModule}
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.API_PUSH_SUBSCRIBER
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

class BoxServiceSpec extends AsyncHmrcSpec with TestData {

  implicit val hc: HeaderCarrier = HeaderCarrier()


  def updateSubscribersRequestWithId(subtype: SubscriptionType): UpdateSubscriberRequest =
    UpdateSubscriberRequest(SubscriberRequest(callBackUrl = endpoint, subscriberType = subtype))

  val updateSubscribersRequestWithOutId: UpdateSubscriberRequest =
    UpdateSubscriberRequest(SubscriberRequest(callBackUrl = endpoint, subscriberType = API_PUSH_SUBSCRIBER))

  trait Setup extends BoxRepositoryMockModule
    with ApiPlatformEventsConnectorMockModule
    with ThirdPartyApplicationConnectorMockModule
    with PushConnectorMockModule
    with ClientServiceMockModule {

    val mockApiPlatformEventsConnector: ApiPlatformEventsConnector = mock[ApiPlatformEventsConnector]

    val objInTest = new BoxService(BoxRepositoryMock.aMock, PushConnectorMock.aMock, ThirdPartyApplicationConnectorMock.aMock, ApiPlatformEventsConnectorMock.aMock, ClientServiceMock.aMock)

    val argumentCaptor = ArgCaptor[Box]

    BoxRepositoryMock.UpdateSubscriber.succeedsWith(boxId, None)

  }

  "BoxService" when {
    "createBox" should {

      "return BoxCreatedResult and call tpa to get application id when box is created" in new Setup {
        BoxRepositoryMock.GetBoxByNameAndClientId.returnsNone()
        BoxRepositoryMock.CreateBox.succeedsWithCreated(box)
        ThirdPartyApplicationConnectorMock.GetApplicationDetails.isSuccessWith(clientId, applicationId)
        ClientServiceMock.FindOrCreateClient.isSuccessWith(clientId, client)

        await(objInTest.createBox(clientId, boxName)) match {
          case BoxCreatedResult(_) =>
            BoxRepositoryMock.GetBoxByNameAndClientId.verifyCalledWith(boxName, clientId)
            ThirdPartyApplicationConnectorMock.GetApplicationDetails.verifyCalledWith(clientId)
            BoxRepositoryMock.CreateBox.verifyCalledWith()
            ClientServiceMock.FindOrCreateClient.verifyCalledWith(clientId)
          case _ => fail
        }

      }


      "return BoxRetrievedResult when box is already exists and verify no attempt to call tpa" in new Setup {

        BoxRepositoryMock.GetBoxByNameAndClientId.succeedsWithOptionalBox(boxName, clientId, Some(box))

        await(objInTest.createBox(clientId, boxName)) match {
          case BoxRetrievedResult(_) =>
            BoxRepositoryMock.GetBoxByNameAndClientId.verifyCalledWith(boxName, clientId)
            ThirdPartyApplicationConnectorMock.GetApplicationDetails.verifyNoInteractions()
            BoxRepositoryMock.CreateBox.verifyNeverCalled()
            ClientServiceMock.verifyZeroInteractions()
          case _ => fail
        }

      }

      "return BoxCreateFailedResult when attempt to get applicationId fails during box creation" in new Setup {
        BoxRepositoryMock.GetBoxByNameAndClientId.succeedsWithOptionalBox(boxName, clientId, None)
        BoxRepositoryMock.CreateBox.succeedsWithCreated(box)
        ThirdPartyApplicationConnectorMock.GetApplicationDetails.failsWith(clientId)
        ClientServiceMock.FindOrCreateClient.isSuccessWith(clientId, client)

        await(objInTest.createBox(clientId, boxName)) match {
          case BoxCreateFailedResult(_) =>
            BoxRepositoryMock.GetBoxByNameAndClientId.verifyCalledWith(boxName, clientId)
            ThirdPartyApplicationConnectorMock.GetApplicationDetails.verifyCalledWith(clientId)
            ClientServiceMock.FindOrCreateClient.verifyCalledWith(clientId)
            BoxRepositoryMock.CreateBox.verifyNeverCalled()
          case _ =>  fail
        }

      }
    }

      "getByBoxNameAndClientId" should {
        "call repository correctly" in new Setup {

          BoxRepositoryMock.GetBoxByNameAndClientId.succeedsWithOptionalBox(boxName, clientId, Some(box))
          await(objInTest.getBoxByNameAndClientId(boxName, clientId))

         BoxRepositoryMock.GetBoxByNameAndClientId.verifyCalledWith(boxName, clientId)
        }

      }

      "getBoxesByClientId" should {
        "delegate to repo and return same list" in new Setup {
          val boxes: List[Box] = List()

          BoxRepositoryMock.GetBoxesByClientId.succeedsWith(clientId, boxes)
          await(objInTest.getBoxesByClientId(clientId)) shouldBe boxes

          BoxRepositoryMock.GetBoxesByClientId.verifyCalledWith(clientId)
        }
      }

      "getAllBoxes" should {
       "cal repository correctly " in  new Setup {
          val boxes: List[Box] = List()

          BoxRepositoryMock.GetAllBoxes.succeedsWith(boxes)

          await(objInTest.getAllBoxes()) shouldBe boxes

          BoxRepositoryMock.GetAllBoxes.verifyCalled()
        }
      }

      "updateCallbackUrl" should {
        val applicationId = ApplicationId.random

        "return CallbackUrlUpdated when process completes successfully" in new Setup {
          val boxWithApplicationId: Box = boxWithExistingPushSubscriber.copy(applicationId = Some(applicationId))
          val newUrl = "callbackUrl"
          val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, newUrl)

          BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, Some(boxWithApplicationId))
          BoxRepositoryMock.UpdateSubscriber.succeedsWith(boxId, Some(boxWithApplicationId))

          PushConnectorMock.ValidateCallbackUrl.succeedsFor(validRequest)
          ApiPlatformEventsConnectorMock.SendCallBackUpdatedEvent.succeedsWith(applicationId, newUrl, boxWithApplicationId)

          await(objInTest.updateCallbackUrl(boxId, validRequest, false)) match {
            case _: CallbackUrlUpdated => ThirdPartyApplicationConnectorMock.verifyZeroInteractions()
              BoxRepositoryMock.UpdateApplicationId.verifyNeverCalled()

              PushConnectorMock.ValidateCallbackUrl.verifyCalled(validRequest)
              ApiPlatformEventsConnectorMock.SendCallBackUpdatedEvent.verifyCalledWith(applicationId, endpoint, newUrl, boxWithApplicationId)

            case _ => fail
          }


        }

        "return CallbackUrlUpdated when box has application id added and callback url is validated" in new Setup {
          BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, Some(boxWithExistingPushSubscriber))
          BoxRepositoryMock.UpdateApplicationId.succeedsWith(boxId, applicationId, boxWithExistingPushSubscriber.copy(applicationId = Some(applicationId)))
          BoxRepositoryMock.UpdateSubscriber.succeedsWith(boxId, Some(box))
          ThirdPartyApplicationConnectorMock.GetApplicationDetails.isSuccessWith(clientId, applicationId)


          ApiPlatformEventsConnectorMock.SendCallBackUpdatedEvent.succeeds()
          val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
          PushConnectorMock.ValidateCallbackUrl.succeedsFor(validRequest)

          await(objInTest.updateCallbackUrl(boxId, validRequest, false)) match {
            case _: CallbackUrlUpdated =>       ThirdPartyApplicationConnectorMock.GetApplicationDetails.verifyCalledWith(clientId)
                                                BoxRepositoryMock.UpdateApplicationId.verifyCalledWith(boxId, applicationId)
                                                PushConnectorMock.ValidateCallbackUrl.verifyCalled(validRequest)
                                                ApiPlatformEventsConnectorMock.SendCallBackUpdatedEvent.verifyCalled()
            case _ => fail
          }

        }

        "return CallbackUrlUpdated when callbackUrl is empty, applicationId exists, and dont call callBackUrl in connector" in new Setup {
          BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, Some(box.copy(applicationId = Some(applicationId))))
          BoxRepositoryMock.UpdateSubscriber.succeedsWith(boxId, Some(box))

          ApiPlatformEventsConnectorMock.SendCallBackUpdatedEvent.succeeds()

          val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "")

           await(objInTest.updateCallbackUrl(boxId, validRequest, false)) match {
             case _: CallbackUrlUpdated =>    PushConnectorMock.verifyZeroInteractions()
                                              ApiPlatformEventsConnectorMock.SendCallBackUpdatedEvent.verifyCalled()
             case _ => fail
           }

        }

        "return UnableToUpdateCallbackUrl when update of box with applicationId with callback fails" in new Setup {
          BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, Some(box.copy(applicationId = Some(applicationId))))
          BoxRepositoryMock.UpdateSubscriber.succeedsWith(boxId, None)

          val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
          PushConnectorMock.ValidateCallbackUrl.succeedsFor(validRequest)

         await(objInTest.updateCallbackUrl(boxId, validRequest, false)) match {
           case _: UnableToUpdateCallbackUrl => PushConnectorMock.ValidateCallbackUrl.verifyCalled(validRequest)
                                                ApiPlatformEventsConnectorMock.verifyZeroInteractions()
           case _ => fail
         }


        }

        "return UnableToUpdateCallbackUrl box has no application id and call to tpa fails" in new Setup {
          BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, Some(box))
          ThirdPartyApplicationConnectorMock.GetApplicationDetails.failsWith(clientId)

          val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
          PushConnectorMock.ValidateCallbackUrl.succeedsFor(validRequest)

          await(objInTest.updateCallbackUrl(boxId, validRequest, false)) match {
            case _: UnableToUpdateCallbackUrl =>  PushConnectorMock.verifyZeroInteractions()
                                                  ApiPlatformEventsConnectorMock.verifyZeroInteractions()
            case _  => fail
          }

        }

        "return UpdateCallbackUrlUnauthorisedResult when clientId of box is different from request clientId" in new Setup {
          BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, Some(box))

          val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(ClientId("someotherId"), "callbackUrl")
           await(objInTest.updateCallbackUrl(boxId, validRequest, false)) match {
             case _: UpdateCallbackUrlUnauthorisedResult =>   PushConnectorMock.verifyZeroInteractions()
                                                              ApiPlatformEventsConnectorMock.verifyZeroInteractions()
             case _ => fail
           }

        }

        "return CallbackValidationFailed when connector call returns false" in new Setup {
          BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, Some(box.copy(applicationId = Some(applicationId))))

          val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
          PushConnectorMock.ValidateCallbackUrl.failsFor(validRequest)

          await(objInTest.updateCallbackUrl(boxId, validRequest, false)) match {
           case _: CallbackValidationFailed =>   ApiPlatformEventsConnectorMock.verifyZeroInteractions()
           case _ => fail
          }

        }

        "return BoxIdNotFound when boxId is not found" in new Setup {
          BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, None)
          val validRequest: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(clientId, "callbackUrl")
         await(objInTest.updateCallbackUrl(boxId, validRequest, false)) match {
           case _: BoxIdNotFound =>     PushConnectorMock.verifyZeroInteractions()
                                        ApiPlatformEventsConnectorMock.verifyZeroInteractions()
           case _ => fail
         }

        }
      }

      "validateBoxOwner" should {

        "return ValidateBoxOwnerSuccessResult when boxId is found and clientId matches" in new Setup {
          BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, Some(box))

          await(objInTest.validateBoxOwner(boxId, clientId)) match {
            case _: ValidateBoxOwnerSuccessResult => succeed
            case _ => fail
          }

        }

        "return ValidateBoxOwnerFailedResult when boxId is found and clientId doesn't match" in new Setup {
          when( BoxRepositoryMock.aMock.findByBoxId(eqTo(boxId))).thenReturn(Future.successful(Some(box)))
          val result: ValidateBoxOwnerResult = await(objInTest.validateBoxOwner(boxId, ClientId(UUID.randomUUID().toString)))
          result.isInstanceOf[ValidateBoxOwnerFailedResult] shouldBe true
        }

        "return ValidateBoxOwnerNotFoundResult when boxId is not found" in new Setup {
          when( BoxRepositoryMock.aMock.findByBoxId(eqTo(boxId))).thenReturn(Future.successful(None))
          val result: ValidateBoxOwnerResult = await(objInTest.validateBoxOwner(boxId, clientId))
          result.isInstanceOf[ValidateBoxOwnerNotFoundResult] shouldBe true
        }
      }



    "deleteBox" should {

      "return BoxDeleteSuccessfulResult when a box is found by boxId" in new Setup {
        val clientManagedBox: Box = box.copy(clientManaged = true)
        BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, Some(clientManagedBox))
        BoxRepositoryMock.DeleteBox.succeeds()

        await(objInTest.deleteBox(clientManagedBox.boxCreator.clientId, clientManagedBox.boxId)) shouldBe BoxDeleteSuccessfulResult()
      }

      "return BoxDeleteAccessDeniedResult when clientManaged is false" in new Setup {
        BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, Some(box))
        BoxRepositoryMock.DeleteBox.failsWith(BoxDeleteAccessDeniedResult())

        await(objInTest.deleteBox(clientId, boxId)) shouldBe BoxDeleteAccessDeniedResult()
      }

      "return BoxDeleteAccessDeniedResult when the given clientId does not match the box's clientId" in new Setup {
        val incorrectClientId: ClientId = ClientId(UUID.randomUUID().toString)
        val clientManagedBox: Box = box.copy(clientManaged = true)

        BoxRepositoryMock.FindByBoxId.succeedsWith(boxId, Some(clientManagedBox))
        BoxRepositoryMock.DeleteBox.verifyNeverCalled()

        await(objInTest.deleteBox(incorrectClientId, boxId)) shouldBe BoxDeleteAccessDeniedResult()
      }
    }



  }
}
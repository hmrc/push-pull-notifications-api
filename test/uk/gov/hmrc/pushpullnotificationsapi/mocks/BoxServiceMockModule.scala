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

package uk.gov.hmrc.pushpullnotificationsapi.mocks

import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, BoxCreateFailedResult, BoxCreatedResult, BoxDeleteAccessDeniedResult, BoxDeleteFailedResult, BoxDeleteNotFoundResult, BoxDeleteSuccessfulResult, BoxId, BoxIdNotFound, BoxRetrievedResult, CallbackUrlUpdated, UpdateCallbackUrlFailedResult, UpdateCallbackUrlSuccessResult, ValidateBoxOwnerFailedResult, ValidateBoxOwnerResult, ValidateBoxOwnerSuccessResult}
import uk.gov.hmrc.pushpullnotificationsapi.services.BoxService

import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

trait BoxServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseBoxServiceMock {

    def aMock: BoxService

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

  object CreateBox {

    def thenFailsWithException(error: String) = {
      when(aMock.createBox(*[ClientId], *, *)(*, *))
      .thenReturn(failed(new RuntimeException(error)))
    }

    def thenSucceedCreated(box: Box) = {
      when(aMock.createBox(*[ClientId], *, *)(*, *)).thenReturn(successful(BoxCreatedResult(box)))
    }

    def thenSucceedRetrieved(box: Box) = {
      when(aMock.createBox(*[ClientId], *, *)(*, *)).thenReturn(successful(BoxRetrievedResult(box)))
    }

    def thenFailsWithBoxName(boxName: String, clientId: ClientId) = {
      when(aMock.createBox(eqTo(clientId), eqTo(boxName), *)(*, *)).thenReturn(successful(BoxCreateFailedResult(s"Box with name :$boxName already exists for clientId: ${clientId.value} but unable to retrieve")))
    }


    def thenFailsWithCreateFailedResult(error: String) = {
      when(aMock.createBox(*[ClientId], *, *)(*, *)).thenReturn(successful(BoxCreateFailedResult(error)))
    }

    def verifyCalledWith(clientId: ClientId, boxName: String, isClientManaged: Boolean) = {
      verify.createBox(eqTo(clientId), eqTo(boxName), eqTo(isClientManaged))(*, *)
    }
  }

    object DeleteBox {
      def failsWithException(msg: String) = {
        when(aMock.deleteBox(*[ClientId], *[BoxId])(*)).thenReturn(failed(new RuntimeException(msg)))
      }

      def failedResultWithText(errorText: String) = {
        when(aMock.deleteBox(*[ClientId], *[BoxId])(*)).thenReturn(successful(BoxDeleteFailedResult(errorText)))
      }


      def failsNotFound() = {
        when(aMock.deleteBox(*[ClientId], *[BoxId])(*)).thenReturn(successful(BoxDeleteNotFoundResult()))
      }

      def failsAccessDenied() = {
        when(aMock.deleteBox(*[ClientId], *[BoxId])(*)).thenReturn(successful(BoxDeleteAccessDeniedResult()))
      }

      def isSuccessful() = {
        when(aMock.deleteBox(*[ClientId], *[BoxId])(*)).thenReturn(successful(BoxDeleteSuccessfulResult()))
      }

      def verifyCalledWith(clientId: ClientId, boxId: BoxId) = {
        verify.deleteBox(eqTo(clientId), eqTo(boxId))(*)
      }

    }

    object GetBoxByNameAndClientId {
      def verifyNoInteractions() = {
        verifyZeroInteractions()
      }

      def verifyCalledWith(boxName: String, clientId: ClientId) = {
        verify.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))
      }

      def thenSuccess(mayBeBox: Option[Box]) = {
        when(aMock.getBoxByNameAndClientId(*, *[ClientId]))
          .thenReturn(successful(mayBeBox))
      }

    }

    object GetAllBoxes {
      def fails(msg: String) = {
        when(aMock.getAllBoxes()(*)).thenReturn(failed(new RuntimeException(msg)))
      }

      def thenSuccess(boxes: List[Box]) = {
        when(aMock.getAllBoxes()(*)).thenReturn(successful(boxes))
      }

      def verifyCalled() {
        verify.getAllBoxes()(*)
      }
    }

    object GetBoxesByClientId {
      def verifyNoInteractions() = {
        verifyZeroInteractions()
      }

      def theFailsWith(clientId: ClientId, exception: RuntimeException) = {
        when(BoxServiceMock.aMock.getBoxesByClientId(eqTo(clientId))).thenReturn(Future.failed(exception))
      }

      def verifyCalledWith(clientId: ClientId) ={
        verify.getBoxesByClientId(eqTo(clientId))
      }

      def thenSuccessWith(clientId: ClientId, boxes: List[Box]) = {
        when(aMock.getBoxesByClientId(eqTo(clientId))).thenReturn(Future.successful(boxes))
      }

    }


    object UpdateCallbackUrl {
      def verifyCalledWith(boxId: BoxId, clientManaged: Boolean = false) ={
        verify.updateCallbackUrl(eqTo(boxId), *, eqTo(clientManaged))(*,*)
      }

      def failsWith(boxId: BoxId, clientManaged: Boolean = false,  result: UpdateCallbackUrlFailedResult) = {
        when(aMock.updateCallbackUrl(eqTo(boxId), *, eqTo(clientManaged))(*, *)).thenReturn(successful(result))
      }

      def verifyNoInteractions() = {
        verifyZeroInteractions()
      }

      def thenSucceedsWith(boxId: BoxId, clientManaged: Boolean = false, result: UpdateCallbackUrlSuccessResult) = {
        when(aMock.updateCallbackUrl(eqTo(boxId), *, eqTo(clientManaged))(*, *)).thenReturn(successful(result))
      }

    }

    object ValidateBoxOwner {
      def verifyNoInteractions() = {
        verifyZeroInteractions()
      }

      def thenFailsWith(boxId: BoxId, clientId: ClientId, result: ValidateBoxOwnerResult) = {
        when(aMock.validateBoxOwner(eqTo(boxId), eqTo(clientId))(*)).thenReturn(successful(result))
      }

      def verifyCalledWith(boxId: BoxId, clientId: ClientId) ={
        verify.validateBoxOwner(eqTo(boxId), eqTo(clientId))(*)
      }

      def thenSucceedsWith(boxId: BoxId, clientId: ClientId) = {
        when(aMock.validateBoxOwner(eqTo(boxId), eqTo(clientId))(*)).thenReturn(successful(ValidateBoxOwnerSuccessResult()))
      }

    }

    //validateBoxOwner

  }

  object BoxServiceMock extends BaseBoxServiceMock {
    val aMock = mock[BoxService](withSettings.lenient())
  }

}

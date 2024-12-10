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

package uk.gov.hmrc.pushpullnotificationsapi.mocks

import scala.concurrent.Future.{failed, successful}

import org.mockito.Strictness.Lenient
import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.services.BoxService

trait BoxServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseBoxServiceMock {

    def aMock: BoxService

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object CreateBox {

      def thenFailsWithException(error: String) = {
        when(aMock.createBox(*[ClientId], *)(*))
          .thenReturn(failed(new RuntimeException(error)))
      }

      def thenSucceedCreated(box: Box) = {
        when(aMock.createBox(*[ClientId], *)(*)).thenReturn(successful(BoxCreatedResult(box)))
      }

      def thenSucceedRetrieved(box: Box) = {
        when(aMock.createBox(*[ClientId], *)(*)).thenReturn(successful(BoxRetrievedResult(box)))
      }

      def thenFailsWithBoxName(boxName: String, clientId: ClientId) = {
        when(aMock.createBox(eqTo(clientId), eqTo(boxName))(*)).thenReturn(successful(
          BoxCreateFailedResult(s"Box with name :$boxName already exists for clientId: ${clientId.value} but unable to retrieve")
        ))
      }

      def thenFailsWithCreateFailedResult(error: String) = {
        when(aMock.createBox(*[ClientId], *)(*)).thenReturn(successful(BoxCreateFailedResult(error)))
      }

      def verifyCalledWith(clientId: ClientId, boxName: String) = {
        verify.createBox(eqTo(clientId), eqTo(boxName))(*)
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
        when(aMock.getAllBoxes()).thenReturn(failed(new RuntimeException(msg)))
      }

      def thenSuccess(boxes: List[Box]) = {
        when(aMock.getAllBoxes()).thenReturn(successful(boxes))
      }

      def verifyCalled(): Unit = {
        verify.getAllBoxes()
      }
    }

    object UpdateCallbackUrl {

      def verifyCalledWith(boxId: BoxId) = {
        verify.updateCallbackUrl(eqTo(boxId), *)(*, *)
      }

      def failsWith(boxId: BoxId, result: UpdateCallbackUrlFailedResult) = {
        when(aMock.updateCallbackUrl(eqTo(boxId), *)(*, *)).thenReturn(successful(result))
      }

      def verifyNoInteractions() = {
        verifyZeroInteractions()
      }

      def thenSucceedsWith(boxId: BoxId, result: UpdateCallbackUrlSuccessResult) = {
        when(aMock.updateCallbackUrl(eqTo(boxId), *)(*, *)).thenReturn(successful(result))
      }
    }
  }

  object BoxServiceMock extends BaseBoxServiceMock {
    val aMock = mock[BoxService](withSettings.strictness(Lenient))
  }

}

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

package uk.gov.hmrc.pushpullnotificationsapi.mocks.repository

import scala.concurrent.Future
import scala.concurrent.Future.successful

import org.mockito.stubbing.ScalaOngoingStubbing
import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository

trait BoxRepositoryMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseBoxRepositoryMock {
    def aMock: BoxRepository

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object CreateBox {
      def verifyNeverCalled() = {}

      def verifyCalledWith() = {
        verify.createBox(*)
      }

      def succeedsWithCreated(box: Box): ScalaOngoingStubbing[Future[CreateBoxResult]] = {
        when(aMock.createBox(*[Box])).thenReturn(successful(BoxCreatedResult(box)))
      }
    }

    object GetBoxByNameAndClientId {

      def verifyCalledWith(boxName: String, clientId: ClientId) = {
        verify.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))
      }

      def returnsNone(): ScalaOngoingStubbing[Future[Option[Box]]] = {
        when(aMock.getBoxByNameAndClientId(*, *[ClientId])).thenReturn(successful(None))
      }

      def succeedsWithOptionalBox(boxName: String, clientId: ClientId, optionalBox: Option[Box]): ScalaOngoingStubbing[Future[Option[Box]]] = {
        when(aMock.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))).thenReturn(successful(optionalBox))
      }
    }

    object UpdateSubscriber {

      def succeedsWith(boxId: BoxId, optionalBox: Option[Box]): ScalaOngoingStubbing[Future[Option[Box]]] = {
        when(aMock.updateSubscriber(eqTo(boxId), *)).thenReturn(successful(optionalBox))
      }
    }

    object GetBoxesByClientId {

      def verifyCalledWith(clientId: ClientId) = {
        verify.getBoxesByClientId(eqTo(clientId))
      }

      def succeedsWith(clientId: ClientId, boxes: List[Box]) = {
        when(aMock.getBoxesByClientId(eqTo(clientId))).thenReturn(successful(boxes))
      }

    }

    object FindByBoxId {

      def succeedsWith(boxId: BoxId, maybeBox: Option[Box]) = {
        when(aMock.findByBoxId(eqTo(boxId))).thenReturn(successful(maybeBox))
      }

    }

    object GetAllBoxes {

      def succeedsWith(boxes: List[Box]) = {
        when(aMock.getAllBoxes()).thenReturn(successful(boxes))
      }

      def verifyCalled() = {
        verify.getAllBoxes()
      }
    }

    object DeleteBox {

      def verifyNeverCalled() = {
        verify(never).deleteBox(*[BoxId])
      }

      def failsWith(result: DeleteBoxResult) = {
        when(aMock.deleteBox(*[BoxId])).thenReturn(successful(result))
      }

      def succeeds() = {
        when(aMock.deleteBox(*[BoxId])).thenReturn(successful(BoxDeleteSuccessfulResult()))
      }

    }

    object UpdateApplicationId {

      def verifyCalledWith(boxId: BoxId, applicationId: ApplicationId) = {
        verify.updateApplicationId(eqTo(boxId), eqTo(applicationId))
      }

      def succeedsWith(boxId: BoxId, applicationId: ApplicationId, box: Box) = {
        when(aMock.updateApplicationId(eqTo(boxId), eqTo(applicationId))).thenReturn(successful(box))
      }

      def verifyNeverCalled() = {
        verify(never).updateApplicationId(*[BoxId], *[ApplicationId])
      }

    }

  }

  object BoxRepositoryMock extends BaseBoxRepositoryMock {
    val aMock = mock[BoxRepository](withSettings.lenient())

  }
}

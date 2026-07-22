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

package uk.gov.hmrc.Confirmationpullnotificationsapi.mocks.connectors

import java.net.URL
import scala.concurrent.Future.successful

import org.mockito.Strictness.Lenient
import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.pushpullnotificationsapi.connectors.ConfirmationConnector
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.OutboundConfirmation
import uk.gov.hmrc.pushpullnotificationsapi.models.{ConfirmationConnectorFailedResult, ConfirmationConnectorSuccessResult}

trait ConfirmationConnectorMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseConfirmationConnectorMock {

    def aMock: ConfirmationConnector

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object SendConfirmation {

      def returnsFailure() = {
        when(aMock.sendConfirmation(*, *)(using *)).thenReturn(successful(ConfirmationConnectorFailedResult("bang boom splat")))
      }

      def throws() = {
        when(aMock.sendConfirmation(*, *)(using *)).thenThrow(new RuntimeException("Boom"))
      }

      def neverCalled() = {
        verify(never).sendConfirmation(*, *)(using *)
      }

      def verifyCalledWith(url: URL) = {
        verify.sendConfirmation(eqTo(url), *[OutboundConfirmation])(using *)
      }

      def isSuccessWith(url: URL, expectedOutBoundConfirmation: OutboundConfirmation) = {
        when(aMock.sendConfirmation(eqTo(url), eqTo(expectedOutBoundConfirmation))(using *)).thenReturn(successful(ConfirmationConnectorSuccessResult()))
      }

    }
  }

  object ConfirmationConnectorMock extends BaseConfirmationConnectorMock {
    val aMock = mock[ConfirmationConnector](withSettings.strictness(Lenient))
  }
}

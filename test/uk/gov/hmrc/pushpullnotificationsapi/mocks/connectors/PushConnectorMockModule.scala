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

package uk.gov.hmrc.pushpullnotificationsapi.mocks.connectors

import scala.concurrent.Future.successful

import org.mockito.captor.ArgCaptor
import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.pushpullnotificationsapi.connectors.PushConnector
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications._

trait PushConnectorMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BasePushConnectorMock {

    def aMock: PushConnector

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object ValidateCallbackUrl {

      def succeedsFor(request: UpdateCallbackUrlRequest) =
        when(aMock.validateCallbackUrl(eqTo(request))).thenReturn(successful(PushConnectorSuccessResult()))

      def failsFor(request: UpdateCallbackUrlRequest) =
        when(aMock.validateCallbackUrl(eqTo(request))).thenReturn(successful(PushConnectorFailedResult("")))

      def verifyCalled(request: UpdateCallbackUrlRequest): Unit = {
        verify(atLeastOnce).validateCallbackUrl(eqTo(request))
      }
    }

    object Send {

      def fails() = {
        val outboundNotificationCaptor = ArgCaptor[OutboundNotification]
        when(aMock.send(outboundNotificationCaptor)(*))
          .thenReturn(successful(PushConnectorFailedResult("some error")))
        outboundNotificationCaptor
      }

      def succeedsFor() = {
        val outboundNotificationCaptor = ArgCaptor[OutboundNotification]
        when(aMock.send(outboundNotificationCaptor)(*)).thenReturn(successful(PushConnectorSuccessResult()))
        outboundNotificationCaptor
      }
    }
  }

  object PushConnectorMock extends BasePushConnectorMock {
    val aMock = mock[PushConnector](withSettings.lenient())
  }
}

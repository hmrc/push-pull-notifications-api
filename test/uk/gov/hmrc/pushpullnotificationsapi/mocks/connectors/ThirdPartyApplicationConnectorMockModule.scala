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

import scala.concurrent.Future.{failed, successful}

import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId}

import uk.gov.hmrc.pushpullnotificationsapi.connectors.{ApplicationResponse, ThirdPartyApplicationConnector}

trait ThirdPartyApplicationConnectorMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseThirdPartyApplicationConnectorMock {

    def aMock: ThirdPartyApplicationConnector

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object GetApplicationDetails {

      def failsWith(clientId: ClientId) = {
        when(aMock.getApplicationDetails(eqTo(clientId))(*)).thenReturn(failed(new RuntimeException("bang")))
      }

      def verifyNoInteractions() = {
        verifyZeroInteractions()
      }

      def verifyCalledWith(clientId: ClientId) = {
        verify.getApplicationDetails(eqTo(clientId))(*)
      }

      def isSuccessWith(clientId: ClientId, applicationId: ApplicationId) = {
        when(aMock.getApplicationDetails(eqTo(clientId))(*))
          .thenReturn(successful(ApplicationResponse(applicationId)))
      }

    }

  }

  object ThirdPartyApplicationConnectorMock extends BaseThirdPartyApplicationConnectorMock {
    val aMock = mock[ThirdPartyApplicationConnector](withSettings.lenient())

  }

}

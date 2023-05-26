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
import uk.gov.hmrc.pushpullnotificationsapi.models.{Client, ClientSecretValue}
import uk.gov.hmrc.pushpullnotificationsapi.services.ClientService

import scala.concurrent.Future.successful

trait ClientServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseClientServiceMock {

    def aMock: ClientService

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object GetClientSecrets {
      def succeedsWith(clientId: ClientId, clientSecret: ClientSecretValue) = {
        when(aMock.getClientSecrets(eqTo(clientId))).thenReturn(successful(Some(Seq(clientSecret))))
      }

      def findsNoneFor(clientId: ClientId) = {
        when(aMock.getClientSecrets(eqTo(clientId))).thenReturn(successful(None))
      }
    }
    object FindOrCreateClient {
      def verifyNeverCalled() = {
        verify(never).findOrCreateClient(*[ClientId])
      }

      def verifyCalledWith(clientId: ClientId) = {
        verify.findOrCreateClient(eqTo(clientId))
      }

      def isSuccessWith(clientId: ClientId, client: Client) = {
        when(aMock.findOrCreateClient(eqTo(clientId))).thenReturn(successful(client))
      }

    }
  }

  object ClientServiceMock extends BaseClientServiceMock {
    val aMock = mock[ClientService](withSettings.lenient())

  }
}
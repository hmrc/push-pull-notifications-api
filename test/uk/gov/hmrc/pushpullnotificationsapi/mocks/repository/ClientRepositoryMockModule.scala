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

package uk.gov.hmrc.pushpullnotificationsapi.mocks.repository

import scala.concurrent.Future.successful

import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.ClientRepository

trait ClientRepositoryMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseClientRepositoryMock {
    def aMock: ClientRepository

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object FindByClientId {

      def verifyCalledWith(clientId: ClientId) = {
        verify.findByClientId(eqTo(clientId))
      }

      def thenClientNotFound(clientId: ClientId) = {
        thenSuccessWith(clientId, None)
      }

      def thenSuccessWith(clientId: ClientId, maybeClient: Option[Client]) = {
        when(aMock.findByClientId(eqTo(clientId))).thenReturn(successful(maybeClient))
      }

    }

    object InsertClient {

      def verifyCalledWith(client: Client) = {
        verify.insertClient(eqTo(client))
      }

      def thenSuccessfulWith(client: Client) = {
        when(aMock.insertClient(eqTo(client))).thenReturn(successful(client))
      }

      def neverCalled() = {
        verify(never).insertClient(*[Client])
      }

    }

  }

  object ClientRepositoryMock extends BaseClientRepositoryMock {
    val aMock = mock[ClientRepository](withSettings.lenient())

  }
}

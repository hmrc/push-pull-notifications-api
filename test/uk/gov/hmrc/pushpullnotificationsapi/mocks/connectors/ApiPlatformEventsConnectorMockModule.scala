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

import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId

import uk.gov.hmrc.pushpullnotificationsapi.connectors.ApiPlatformEventsConnector
import uk.gov.hmrc.pushpullnotificationsapi.models.Box

trait ApiPlatformEventsConnectorMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseApiPlatformEventsConnectorMock {

    def aMock: ApiPlatformEventsConnector

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object SendCallBackUpdatedEvent {

      def verifyCalledWith(applicationId: ApplicationId, endpoint: String, newUrl: String, box: Box) = {
        verify.sendCallBackUpdatedEvent(eqTo(applicationId), eqTo(endpoint), eqTo(newUrl), eqTo(box))(*)
      }

      def verifyCalled() = {
        verify.sendCallBackUpdatedEvent(*[ApplicationId], *, *, *)(*)
      }

      def succeeds() = {
        when(aMock.sendCallBackUpdatedEvent(*[ApplicationId], *, *, *)(*)).thenReturn(successful(true))
      }

      def succeedsWith(applicationId: ApplicationId, newUrl: String, box: Box) = {
        when(aMock.sendCallBackUpdatedEvent(eqTo(applicationId), *, eqTo(newUrl), eqTo(box))(*)).thenReturn(successful(true))
      }

    }
  }

  object ApiPlatformEventsConnectorMock extends BaseApiPlatformEventsConnectorMock {
    val aMock = mock[ApiPlatformEventsConnector](withSettings.lenient())

  }

}

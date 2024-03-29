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

import org.mockito.Strictness.Lenient
import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.pushpullnotificationsapi.services.HmacService

trait HmacServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseHmacServiceMock {

    def aMock: HmacService

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object Sign {

      def succeedsWith(expectedSignature: String) =
        when(aMock.sign(any, any)).thenReturn(expectedSignature)
    }

  }

  object HmacServiceMock extends BaseHmacServiceMock {
    val aMock = mock[HmacService](withSettings.strictness(Lenient))
  }
}

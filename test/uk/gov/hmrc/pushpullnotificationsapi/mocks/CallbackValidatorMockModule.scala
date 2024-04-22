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

import scala.concurrent.Future.successful

import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar, Strictness}

import uk.gov.hmrc.pushpullnotificationsapi.models.{CallbackValidation, CallbackValidationResult}
import uk.gov.hmrc.pushpullnotificationsapi.services.CallbackValidator

trait CallbackValidatorMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseCallbackVailidatorMock {

    def aMock: CallbackValidator

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object validateCallback {

      def successfulResult() = {
        when(aMock.validateCallback(CallbackValidation("someUrl"))).thenReturn(successful(CallbackValidationResult(successful = false)))
      }
    }
  }

  object CallbackValidatorMock extends BaseCallbackVailidatorMock {
    val aMock = mock[CallbackValidator](withSettings.strictness(Strictness.Lenient))
  }
}

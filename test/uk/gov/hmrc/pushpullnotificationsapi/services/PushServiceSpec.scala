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

package uk.gov.hmrc.pushpullnotificationsapi.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.mocks.CallbackValidatorMockModule
import uk.gov.hmrc.pushpullnotificationsapi.models.{CallbackValidation, CallbackValidationResult, PushServiceFailedResult, PushServiceSuccessResult, UpdateCallbackUrlRequest}

class PushServiceSpec extends AsyncHmrcSpec {

  trait Setup extends CallbackValidatorMockModule {
    val mockCallbackValidator = mock[CallbackValidator]
    val objInTest = new PushService(mockCallbackValidator)
  }

  "validate Callback Url" should {

    "return PushServiceSuccessResult when result is successful" in new Setup {
      when(mockCallbackValidator.validateCallback(CallbackValidation("someUrl"))).thenReturn(successful(CallbackValidationResult(successful = true)))
      val result = await(objInTest.validateCallbackUrl(UpdateCallbackUrlRequest(ClientId("someClientId"), "someUrl")))
      result shouldBe a[PushServiceSuccessResult]
    }

    "return PushServiceFailedResult with an unknown error when result fails" in new Setup {
      when(mockCallbackValidator.validateCallback(CallbackValidation("someUrl"))).thenReturn(successful(CallbackValidationResult(successful = false)))
      val result = await(objInTest.validateCallbackUrl(UpdateCallbackUrlRequest(ClientId("someClientId"), "someUrl")))
      result shouldBe PushServiceFailedResult("Unknown Error")
    }

    "return PushServiceFailedResult with an error message when result fails" in new Setup {
      when(mockCallbackValidator.validateCallback(CallbackValidation("someUrl"))).thenReturn(successful(CallbackValidationResult(successful = false, Some("Some error message"))))
      val result = await(objInTest.validateCallbackUrl(UpdateCallbackUrlRequest(ClientId("someClientId"), "someUrl")))
      result shouldBe PushServiceFailedResult("Some error message")
    }
  }
}

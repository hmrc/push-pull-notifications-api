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

package uk.gov.hmrc.pushpullnotificationsapi.services

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.test.Helpers._
import uk.gov.hmrc.http.{BadRequestException, JsValidationException, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.connectors.OutboundProxyConnector
import uk.gov.hmrc.pushpullnotificationsapi.models.{CallbackValidation, CallbackValidationResult}

class CallbackValidatorSpec extends HmrcSpec {

  trait Setup {
    val mockOutboundProxyConnector: OutboundProxyConnector = mock[OutboundProxyConnector]
    val mockChallengeGenerator: ChallengeGenerator = mock[ChallengeGenerator]

    val underTest = new CallbackValidator(mockOutboundProxyConnector, mockChallengeGenerator)
  }

  "validateCallback" should {
    val callbackValidation = CallbackValidation("https://example.com/post-handler")
    val expectedChallenge = randomUUID.toString

    "return a successful result when the returned challenge matches the expected challenge" in new Setup {
      when(mockOutboundProxyConnector.validateCallback(callbackValidation, expectedChallenge)).thenReturn(successful(expectedChallenge))
      when(mockChallengeGenerator.generateChallenge).thenReturn(expectedChallenge)

      val result: CallbackValidationResult = await(underTest.validateCallback(callbackValidation))

      result shouldBe CallbackValidationResult(successful = true)
    }

    "return an error response when the returned challenge does not match the expected challenge" in new Setup {
      when(mockOutboundProxyConnector.validateCallback(callbackValidation, expectedChallenge)).thenReturn(successful("invalidChallenge"))
      when(mockChallengeGenerator.generateChallenge).thenReturn(expectedChallenge)

      val result: CallbackValidationResult = await(underTest.validateCallback(callbackValidation))

      result shouldBe CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
    }

    "return an error response when there is an upstream error" in new Setup {
      when(mockOutboundProxyConnector.validateCallback(callbackValidation, expectedChallenge))
        .thenReturn(failed(UpstreamErrorResponse("unexpected error", INTERNAL_SERVER_ERROR)))
      when(mockChallengeGenerator.generateChallenge).thenReturn(expectedChallenge)

      val result: CallbackValidationResult = await(underTest.validateCallback(callbackValidation))

      result shouldBe CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
    }

    "return an error response when there is an HTTP exception" in new Setup {
      when(mockOutboundProxyConnector.validateCallback(callbackValidation, expectedChallenge))
        .thenReturn(failed(new BadRequestException("bad request")))
      when(mockChallengeGenerator.generateChallenge).thenReturn(expectedChallenge)

      val result: CallbackValidationResult = await(underTest.validateCallback(callbackValidation))

      result shouldBe CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
    }

    "return an error response when there is a JS validation exception" in new Setup {
      when(mockOutboundProxyConnector.validateCallback(callbackValidation, expectedChallenge))
        .thenReturn(failed(new JsValidationException("", "", CallbackValidationResult.getClass, "")))
      when(mockChallengeGenerator.generateChallenge).thenReturn(expectedChallenge)

      val result: CallbackValidationResult = await(underTest.validateCallback(callbackValidation))

      result shouldBe CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
    }

    "return an error response when there is an IllegalArguementException" in new Setup {
      when(mockOutboundProxyConnector.validateCallback(callbackValidation, expectedChallenge))
        .thenReturn(failed(new IllegalArgumentException("Invalid host example.com")))
      when(mockChallengeGenerator.generateChallenge).thenReturn(expectedChallenge)

      val result: CallbackValidationResult = await(underTest.validateCallback(callbackValidation))

      result shouldBe CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
    }

    "return an error response when there is an unexpected error" in new Setup {
      when(mockOutboundProxyConnector.validateCallback(callbackValidation, expectedChallenge)).thenReturn(failed(new RuntimeException("unexpected error")))
      when(mockChallengeGenerator.generateChallenge).thenReturn(expectedChallenge)

      val result: CallbackValidationResult = await(underTest.validateCallback(callbackValidation))

      result shouldBe CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
    }
  }
}

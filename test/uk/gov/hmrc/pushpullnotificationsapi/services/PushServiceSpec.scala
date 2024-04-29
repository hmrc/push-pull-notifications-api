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
import scala.concurrent.Future
import scala.concurrent.Future.successful

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.connectors.OutboundProxyConnector
import uk.gov.hmrc.pushpullnotificationsapi.mocks.CallbackValidatorMockModule
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.OutboundNotification
import uk.gov.hmrc.pushpullnotificationsapi.models.{CallbackValidation, CallbackValidationResult, PushServiceFailedResult, PushServiceSuccessResult, UpdateCallbackUrlRequest}

class PushServiceSpec extends AsyncHmrcSpec {

  trait Setup extends CallbackValidatorMockModule {
    val mockCallbackValidator = mock[CallbackValidator]
    val mockOutboundProxyConnector = mock[OutboundProxyConnector]
    val objInTest = new PushService(mockCallbackValidator, mockOutboundProxyConnector)
  }

  "validateCallbackUrl" should {

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

  "handleNotification" should {

    "return PushServiceFailedResult when notification payload is empty" in new Setup {
      val result = await(objInTest.handleNotification(OutboundNotification("someUrl", List(), "")))
      result shouldBe PushServiceFailedResult("Invalid OutboundNotification")
    }

    "return PushServiceFailedResult when notification destinationUrl is empty" in new Setup {
      val result = await(objInTest.handleNotification(OutboundNotification("", List(), "payload")))
      result shouldBe PushServiceFailedResult("Invalid OutboundNotification")
    }

    val validNotification = OutboundNotification("url", List(), "payload")

    "return PushServiceSuccessResult when 200 status code is returned from connector" in new Setup {
      when(mockOutboundProxyConnector.postNotification(validNotification)).thenReturn(successful(200))
      val result = await(objInTest.handleNotification(validNotification))
      result shouldBe PushServiceSuccessResult()
    }

    "return PushServiceFailedResult when non 200 status code is returned from connector" in new Setup {
      when(mockOutboundProxyConnector.postNotification(validNotification)).thenReturn(successful(201))
      val result = await(objInTest.handleNotification(validNotification))
      result shouldBe PushServiceFailedResult("HTTP Status Code was not 200")
    }

    "return PushServiceFailedResult when connector throws an exception" in new Setup {
      when(mockOutboundProxyConnector.postNotification(validNotification)).thenReturn(Future.failed(new RuntimeException("Some error")))
      val result = await(objInTest.handleNotification(validNotification))
      result shouldBe PushServiceFailedResult("An exception occured: java.lang.RuntimeException: Some error")
    }
  }
}

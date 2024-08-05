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

package uk.gov.hmrc.pushpullnotificationsapi.connectors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

import org.mockito.captor.ArgCaptor

import play.api.Logger
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2

import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ForwardedHeader, OutboundNotification}

class OutboundProxyConnectorSpec extends HmrcSpec {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup {
    val mockAppConfig: AppConfig = mock[AppConfig]
    val mockDefaultHttpClient = mock[HttpClient] // TODO: Remove this line
    val mockHttpClient = mock[HttpClientV2]
    val mockLogger: Logger = mock[Logger]

    when(mockAppConfig.allowedHostList).thenReturn(List.empty)

    val underTest = new OutboundProxyConnector(mockAppConfig, mockHttpClient) {
      override lazy val logger: Logger = mockLogger
    }
  }

  "OutboundProxyConnector" should {
    "should use the proxy when configured" in new Setup {
      when(mockAppConfig.useProxy).thenReturn(true)

      ???
//      underTest.httpClient shouldBe mockProxiedHttpClient
    }

    "should use the normal client when configured" in new Setup {
      when(mockAppConfig.useProxy).thenReturn(false)

      ???
//      underTest.httpClient shouldBe mockDefaultHttpClient
    }
  }

  "validateCallbackUrl" should {
    import OutboundProxyConnector.CallbackValidationResponse
    val challenge = "foobar"
    val returnedChallenge = CallbackValidationResponse(challenge)
    val callbackUrlPath = "/callback"

    "fail when the callback URL does not match pattern and configured to validate" in new Setup {
      when(mockAppConfig.validateHttpsCallbackUrl).thenReturn(true)
      val callbackValidation = CallbackValidation("http://localhost" + callbackUrlPath)

      val exception = intercept[IllegalArgumentException] {
        await(underTest.validateCallback(callbackValidation, challenge))
      }

      exception.getMessage shouldBe s"Invalid destination URL ${callbackValidation.callbackUrl}"
    }

    "succeed when the callback URL does match pattern and configured to validate" in new Setup {
      when(mockAppConfig.validateHttpsCallbackUrl).thenReturn(true)
      val callbackValidation = CallbackValidation("https://localhost" + callbackUrlPath)

      when(mockDefaultHttpClient.GET[CallbackValidationResponse](*, *, *)(*, *, *)).thenReturn(successful(returnedChallenge))

      await(underTest.validateCallback(callbackValidation, challenge)) shouldBe challenge
    }

    "make a successful request when the host matches a host in the list" in new Setup {
      val host = "example.com"
      when(mockAppConfig.allowedHostList).thenReturn(List(host))
      val callbackValidation = CallbackValidation(s"https://$host$callbackUrlPath")

      when(mockDefaultHttpClient.GET[CallbackValidationResponse](*, *, *)(*, *, *)).thenReturn(successful(returnedChallenge))

      await(underTest.validateCallback(callbackValidation, challenge)) shouldBe challenge
    }

    "fail when the host does not match any of the hosts in the list" in new Setup {
      val host = "example.com"
      when(mockAppConfig.allowedHostList).thenReturn(List(host))
      val callbackValidation = CallbackValidation(s"https://badexample.com/$callbackUrlPath")

      val exception = intercept[IllegalArgumentException] {
        await(underTest.validateCallback(callbackValidation, challenge))
      }
      exception.getMessage shouldBe "Invalid host badexample.com"
      verifyZeroInteractions(mockDefaultHttpClient)
    }
  }

  "postNotification" should {
    val url = "/destination"

    "fail when the destination URL does not match pattern and configured to validate" in new Setup {
      when(mockAppConfig.validateHttpsCallbackUrl).thenReturn(true)

      val destinationUrl = "http://localhost" + url
      val notification: OutboundNotification = OutboundNotification(destinationUrl, List.empty, """{"key": "value"}""")

      val exception = intercept[IllegalArgumentException] {
        await(underTest.postNotification(notification))
      }

      exception.getMessage shouldBe s"Invalid destination URL $destinationUrl"
    }

    "succeed when the destination URL does match pattern and configured to validate" in new Setup {
      when(mockAppConfig.validateHttpsCallbackUrl).thenReturn(true)

      val destinationUrl = "https://localhost" + url
      val notification: OutboundNotification = OutboundNotification(destinationUrl, List.empty, """{"key": "value"}""")

      when(mockDefaultHttpClient.POSTString[Either[UpstreamErrorResponse, HttpResponse]](eqTo(destinationUrl), *, *)(*, *, *)).thenReturn(successful(Right(HttpResponse(OK, ""))))

      await(underTest.postNotification(notification)) shouldBe OK
    }

    "handle post throwing gatewayTimeoutException" in new Setup {
      when(mockAppConfig.validateHttpsCallbackUrl).thenReturn(true)

      val destinationUrl = "https://localhost" + url
      val notification: OutboundNotification = OutboundNotification(destinationUrl, List.empty, """{"key": "value"}""")

      when(mockDefaultHttpClient.POSTString[Either[UpstreamErrorResponse, HttpResponse]](eqTo(destinationUrl), *, *)(*, *, *)).thenReturn(failed(new GatewayTimeoutException("Bang")))

      await(underTest.postNotification(notification)) shouldBe GATEWAY_TIMEOUT
    }

    "handle post throwing badGatewayExeption" in new Setup {
      when(mockAppConfig.validateHttpsCallbackUrl).thenReturn(true)

      val destinationUrl = "https://localhost" + url
      val notification: OutboundNotification = OutboundNotification(destinationUrl, List.empty, """{"key": "value"}""")

      when(mockDefaultHttpClient.POSTString[Either[UpstreamErrorResponse, HttpResponse]](eqTo(destinationUrl), *, *)(*, *, *)).thenReturn(failed(new BadGatewayException("Bang")))

      await(underTest.postNotification(notification)) shouldBe BAD_GATEWAY
    }

    "pass the payload " in new Setup {
      when(mockAppConfig.validateHttpsCallbackUrl).thenReturn(true)

      val destinationUrl = "https://localhost" + url
      val notification: OutboundNotification = OutboundNotification(destinationUrl, List.empty, """{"key": "value"}""")

      when(mockDefaultHttpClient.POSTString[Either[UpstreamErrorResponse, HttpResponse]](*, eqTo(notification.payload), *)(*, *, *)).thenReturn(successful(Right(HttpResponse(OK, ""))))

      await(underTest.postNotification(notification)) shouldBe OK
    }

    "pass any extra headers" in new Setup {
      when(mockAppConfig.validateHttpsCallbackUrl).thenReturn(true)

      val destinationUrl = "https://localhost" + url
      val notification: OutboundNotification = OutboundNotification(destinationUrl, List(ForwardedHeader("THIS", "THAT")), """{"key": "value"}""")

      when(mockDefaultHttpClient.POSTString[Either[UpstreamErrorResponse, HttpResponse]](*, *, *)(*, *, *)).thenReturn(successful(Right(HttpResponse(OK, ""))))

      await(underTest.postNotification(notification)) shouldBe OK

      val headers = ArgCaptor[Seq[(String, String)]]
      verify(mockDefaultHttpClient).POSTString(*, *, headers.capture)(*, *, *)
      headers.value should contain("THIS" -> "THAT")
    }
  }
}

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

package uk.gov.hmrc.pushpullnotificationsapi.connectors

import scala.concurrent.ExecutionContext

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models.CallbackValidation
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ForwardedHeader, OutboundNotification}

class OutboundProxyConnectorISpec extends AsyncHmrcSpec with WireMockSupport with GuiceOneAppPerSuite with HttpClientV2Support {

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "validateHttpsCallbackUrl" -> false,
        "metrics.enabled" -> false,
        "auditing.enabled" -> false
      )

  trait Setup {
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

    val mockAppConfig: AppConfig = mock[AppConfig]
    when(mockAppConfig.allowedHostList).thenReturn(List("localhost"))
    when(mockAppConfig.validateCallbackUrlIsHttps).thenReturn(false)

    val underTest = new OutboundProxyConnector(mockAppConfig, httpClientV2)
  }

  trait SetupWithProxy {
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

    val mockAppConfig: AppConfig = mock[AppConfig]
    when(mockAppConfig.useProxy).thenReturn(true)
    when(mockAppConfig.allowedHostList).thenReturn(List("localhost"))
    when(mockAppConfig.validateCallbackUrlIsHttps).thenReturn(false)

    val underTest = new OutboundProxyConnector(mockAppConfig, httpClientV2)
  }

  "validateCallbackUrl" should {
    val challenge = "foobar"
    val callbackUrlPath = "/callback"

    def stubValidateCallback(url: String, challenge: String) =
      stubFor(
        get(urlPathEqualTo(url))
          .withQueryParam("challenge", equalTo(challenge))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(s"""{"challenge":"$challenge"}""")
          )
      )

    "succeed when the callback URL does validate" in new Setup {
      val url = s"http://localhost:$wireMockPort$callbackUrlPath"
      stubValidateCallback(callbackUrlPath, challenge)

      await(underTest.validateCallback(CallbackValidation(url), challenge)) shouldBe challenge
    }

    "succeed when the callback URL has a query parameter" in new Setup {
      val url = s"http://localhost:$wireMockPort$callbackUrlPath?key=value"
      stubValidateCallback(callbackUrlPath, challenge)

      await(underTest.validateCallback(CallbackValidation(url), challenge)) shouldBe challenge
    }

    "fail when the callback URL does not validate" in new Setup {
      val url = s"http://abc.com:6001$callbackUrlPath"

      val exception = intercept[IllegalArgumentException] {
        await(underTest.validateCallback(CallbackValidation(url), challenge))
      }

      exception.getMessage shouldBe s"Invalid host abc.com"
    }
  }

  "postNotification" should {
    val url = "/destination"

    def stubPostNotification(url: String, notification: OutboundNotification, status: Int) =
      stubFor(
        post(urlPathEqualTo(url))
          .withHeader(CONTENT_TYPE, equalTo("application/json"))
          .withRequestBody(equalTo(notification.payload))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )

    def stubPostNotificationWithAHeader(url: String, notification: OutboundNotification, status: Int) =
      stubFor(
        post(urlPathEqualTo(url))
          .withHeader(CONTENT_TYPE, equalTo("application/json"))
          .withHeader(notification.forwardedHeaders.head.key, equalTo(notification.forwardedHeaders.head.value))
          .withRequestBody(equalTo(notification.payload))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )

    "succeed when the destination URL does validate" in new Setup {
      val destinationUrl = s"http://localhost:$wireMockPort" + url
      val notification: OutboundNotification = OutboundNotification(destinationUrl, List.empty, """{"key":"value"}""")
      stubPostNotification(url, notification, OK)

      await(underTest.postNotification(notification)) shouldBe OK
    }

    "fail when the destination URL does not validate" in new Setup {
      val destinationUrl = s"http://abc.com:6001" + url
      val notification: OutboundNotification = OutboundNotification(destinationUrl, List.empty, """{"key":"value"}""")

      val exception = intercept[IllegalArgumentException] {
        await(underTest.postNotification(notification))
      }

      exception.getMessage shouldBe s"Invalid host abc.com"
    }

    "fail when the called service returns an error" in new Setup {
      val destinationUrl = s"http://localhost:$wireMockPort" + url
      val notification: OutboundNotification = OutboundNotification(destinationUrl, List.empty, """{"key":"value"}""")
      stubPostNotification(url, notification, BAD_REQUEST)

      await(underTest.postNotification(notification)) shouldBe BAD_REQUEST
    }

    "pass any extra headers" in new Setup {
      val destinationUrl = s"http://localhost:$wireMockPort" + url
      val notification: OutboundNotification = OutboundNotification(destinationUrl, List(ForwardedHeader("THIS", "THAT")), """{"key":"value"}""")
      stubPostNotificationWithAHeader(url, notification, OK)

      await(underTest.postNotification(notification)) shouldBe OK
    }
  }
}

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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationId, NotificationStatus, OutboundConfirmation}
import uk.gov.hmrc.pushpullnotificationsapi.models.{ConfirmationConnectorFailedResult, ConfirmationConnectorSuccessResult, ConfirmationId, PrivateHeader}
import uk.gov.hmrc.pushpullnotificationsapi.support.{MetricsTestSupport, PushGatewayService, WireMockSupport}

class ConfirmationConnectorISpec extends AsyncHmrcSpec with WireMockSupport with GuiceOneAppPerSuite with PushGatewayService with MetricsTestSupport with FixedClock {
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override def commonStubs(): Unit = givenCleanMetricRegistry()

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "microservice.services.push-pull-notifications-gateway.port" -> wireMockPort
      )

  trait SetUp {
    val objInTest: ConfirmationConnector = app.injector.instanceOf[ConfirmationConnector]
  }

  "Confirmation Connector" should {

    "when it returns 200" in new SetUp() {

      stubFor(post(urlEqualTo("/")).withHeader("Content-Type", equalTo("application/json"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{}")
        ))
      val result = await(
        objInTest.sendConfirmation(
          wireMockBaseUrl,
          OutboundConfirmation(ConfirmationId.random, NotificationId.random, "1", NotificationStatus.ACKNOWLEDGED, Some(instant), List.empty)
        )
      )

      result shouldBe ConfirmationConnectorSuccessResult()
    }

    "get sent the correct json payload" in new SetUp() {

      val confirmationId = ConfirmationId.random
      val notificationId = NotificationId.random
      val instantAsText = Json.toJson(instant).toString
      val rawText =
        s"""{"confirmationId":"$confirmationId","notificationId":"${notificationId}","version":"1","status":"ACKNOWLEDGED","dateTime":$instantAsText,"privateHeaders":[{"name":"f1","value":"v1"}]}"""

      stubFor(
        post(urlEqualTo("/"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(equalTo(rawText))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody("{}")
          )
      )
      val result = await(
        objInTest.sendConfirmation(
          wireMockBaseUrl,
          OutboundConfirmation(confirmationId, notificationId, "1", NotificationStatus.ACKNOWLEDGED, Some(instant), List(PrivateHeader("f1", "v1")))
        )
      )

      result shouldBe ConfirmationConnectorSuccessResult()
    }

    "when it returns 400" in new SetUp() {

      stubFor(post(urlEqualTo("/"))
        .willReturn(
          aResponse()
            .withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withBody("{}")
        ))
      val result = await(
        objInTest.sendConfirmation(
          wireMockBaseUrl,
          OutboundConfirmation(ConfirmationId.random, NotificationId.random, "1", NotificationStatus.ACKNOWLEDGED, Some(instant), List.empty)
        )
      )

      result shouldBe a[ConfirmationConnectorFailedResult]
    }
  }
}

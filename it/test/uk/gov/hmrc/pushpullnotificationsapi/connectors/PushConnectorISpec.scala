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

import java.util.UUID

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, NotificationId, OutboundNotification}
import uk.gov.hmrc.pushpullnotificationsapi.support.{MetricsTestSupport, PushGatewayService, WireMockSupport}

class PushConnectorISpec extends AsyncHmrcSpec with WireMockSupport with GuiceOneAppPerSuite with PushGatewayService with MetricsTestSupport {
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override def commonStubs(): Unit = givenCleanMetricRegistry()

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "microservice.services.push-pull-notifications-gateway.port" -> wireMockPort,
        "microservice.services.push-pull-notifications-gateway.authorizationKey" -> "iampushpullapi"
      )

  trait SetUp {

    val notificationResponse: String =
      Json.toJson(NotificationResponse(NotificationId(UUID.randomUUID), BoxId(UUID.randomUUID), MessageContentType.APPLICATION_JSON, "{}")).toString
    val objInTest: PushConnector = app.injector.instanceOf[PushConnector]
  }

  "PushConnector send" should {
    "return PushConnectorSuccessResult when OK result and Success true is returned in payload" in new SetUp() {
      primeGatewayServiceWithBody(Status.OK)
      val notification: OutboundNotification = OutboundNotification("someDestination", List.empty, notificationResponse)
      val result: PushConnectorResult = await(objInTest.send(notification))
      result shouldBe PushConnectorSuccessResult()
    }

    "return PushConnectorFailedResult UnprocessableEntity when OK result and Success false is returned in payload" in new SetUp() {
      primeGatewayServiceWithBody(Status.OK, successfulResult = false)
      val notification: OutboundNotification = OutboundNotification("someDestination", List.empty, notificationResponse)
      val result: PushConnectorResult = await(objInTest.send(notification))
      result.isInstanceOf[PushConnectorFailedResult] shouldBe true
      val castResult: PushConnectorFailedResult = result.asInstanceOf[PushConnectorFailedResult]
      castResult.errorMessage shouldBe "PPNS Gateway was unable to successfully deliver notification"
    }

    "return PushConnectorFailedResult Notfound when Notfound result" in new SetUp() {
      primeGatewayServicPostNoBody(Status.NOT_FOUND)
      val notification: OutboundNotification = OutboundNotification("someDestination", List.empty, notificationResponse)
      val result: PushConnectorResult = await(objInTest.send(notification))
      result.isInstanceOf[PushConnectorFailedResult] shouldBe true
    }
  }

  "PushConnector validate callback" should {

    "return PushConnectorSuccessResult when validate-callback call returns true" in new SetUp() {
      primeGatewayServiceValidateCallBack(Status.OK)

      val request: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(ClientId.random, "calbackUrl")
      val result = await(objInTest.validateCallbackUrl(request))

      result.isInstanceOf[PushConnectorSuccessResult] shouldBe true
    }

    "return PushConnectorFailedResult when validate-callback call returns false" in new SetUp() {
      primeGatewayServiceValidateCallBack(Status.OK, successfulResult = false, Some("someError"))

      val request: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(ClientId.random, "calbackUrl")
      val result = await(objInTest.validateCallbackUrl(request))

      result.isInstanceOf[PushConnectorFailedResult] shouldBe true
      val convertedResult = result.asInstanceOf[PushConnectorFailedResult]
      convertedResult.errorMessage shouldBe "someError"
    }

    "return PushConnectorFailedResult when validate-callback call returns false with no error message" in new SetUp() {
      primeGatewayServiceValidateCallBack(Status.OK, successfulResult = false)

      val request: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(ClientId.random, "calbackUrl")
      val result = await(objInTest.validateCallbackUrl(request))

      result.isInstanceOf[PushConnectorFailedResult] shouldBe true
      val convertedResult = result.asInstanceOf[PushConnectorFailedResult]
      convertedResult.errorMessage shouldBe "Unknown Error"
    }

    "return summat when validate-callback call returns false" in new SetUp() {
      primeGatewayServiceValidateNoBody(Status.BAD_REQUEST)

      val request: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(ClientId.random, "calbackUrl")
      val result = await(objInTest.validateCallbackUrl(request))

      result.isInstanceOf[PushConnectorFailedResult] shouldBe true
    }
  }

}

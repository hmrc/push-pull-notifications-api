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

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaborators, ApplicationWithCollaboratorsFixtures}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.support.{MetricsTestSupport, ThirdPartyApplicationService, WireMockSupport}
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

class ThirdPartyApplicationConnectorISpec
    extends AsyncHmrcSpec
    with WireMockSupport
    with GuiceOneAppPerSuite
    with MetricsTestSupport
    with ThirdPartyApplicationService
    with ApplicationWithCollaboratorsFixtures
    with TestData {

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
        "microservice.services.third-party-application.port" -> wireMockPort
      )

  trait SetUp {
    val objInTest: ThirdPartyApplicationConnector = app.injector.instanceOf[ThirdPartyApplicationConnector]
  }

  "getApplicationDetails" should {
    val clientId = ClientId.random

    "retrieve application record based on provided clientId" in new SetUp() {

      val jsonResponse = Json.toJson(standardApp.withId(applicationId).modify(_.copy(clientId = clientId))).toString()

      primeApplicationQueryEndpoint(OK, jsonResponse, clientId)

      val result: ApplicationWithCollaborators = await(objInTest.getApplicationDetails(clientId))

      result.id shouldBe applicationId
    }

    "return failed Future if TPA returns a 404" in new SetUp {
      primeApplicationQueryEndpoint(NOT_FOUND, "", clientId)

      intercept[UpstreamErrorResponse] {
        await(objInTest.getApplicationDetails(clientId))
      }.statusCode shouldBe NOT_FOUND
    }
  }
}

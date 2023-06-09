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

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, CREATED}
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.support.{ApiPlatformEventsService, MetricsTestSupport, WireMockSupport}
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId}

class ApiPlatformEventsConnectorISpec extends AsyncHmrcSpec with WireMockSupport with GuiceOneAppPerSuite with MetricsTestSupport with ApiPlatformEventsService {

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
        "microservice.services.api-platform-events.port" -> wireMockPort
      )

  trait SetUp {
    val objInTest: ApiPlatformEventsConnector = app.injector.instanceOf[ApiPlatformEventsConnector]
  }

  "sendCallBackUpdatedEvent" should {
    val box = Box(BoxId.random, "foo box", BoxCreator(ClientId.random))

    "return true when call to api platform events returns CREATED" in new SetUp {
      primeCallBackUpdatedEndpoint(CREATED)

      val result = await(objInTest.sendCallBackUpdatedEvent(ApplicationId.random /*("12344")*/, "oldUrl", "newUrl", box))
      result shouldBe true
    }

    "return false when call to api platform events returns anything other than CREATED" in new SetUp {
      primeCallBackUpdatedEndpoint(BAD_REQUEST)

      val result = await(objInTest.sendCallBackUpdatedEvent(ApplicationId.random /*("12344")*/, "oldUrl", "newUrl", box))
      result shouldBe false
    }

  }

}

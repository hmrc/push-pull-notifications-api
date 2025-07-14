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

package uk.gov.hmrc.pushpullnotificationsapi.controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{status => _, _}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.apache.pekko.stream.Materializer
import org.scalatest.{BeforeAndAfterEach, TestData}
import org.scalatestplus.play.guice.GuiceOneAppPerTest

import play.api.http.Status.{NO_CONTENT, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Mode}

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.platform.controllers.DocumentationController

class DocumentationControllerISpec extends AsyncHmrcSpec with GuiceOneAppPerTest with BeforeAndAfterEach {

  val stubHost = "localhost"
  val stubPort = sys.env.getOrElse("WIREMOCK_SERVICE_LOCATOR_PORT", "11112").toInt
  val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  override def newAppForTest(testData: TestData): Application = GuiceApplicationBuilder()
    .configure("run.mode" -> "Stub")
    .configure(Map(
      "appName" -> "application-name",
      "appUrl" -> "https://example.com",
      "auditing.enabled" -> false,
      "Test.microservice.services.service-locator.host" -> stubHost,
      "Test.microservice.services.service-locator.port" -> stubPort,
      "apiStatus" -> "ALPHA"
    ))
    .in(Mode.Test).build()

  override def beforeEach(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
    stubFor(post(urlMatching("http://localhost:11112/registration")).willReturn(aResponse().withStatus(NO_CONTENT)))
  }

  trait Setup {
    implicit def mat: Materializer = app.injector.instanceOf[Materializer]
    val documentationController = app.injector.instanceOf[DocumentationController]
    val request = FakeRequest()
  }

  "microservice" should {
    "provide definition endpoint and documentation endpoint for each api" in new Setup {
      val result = documentationController.definition()(request)
      status(result) shouldBe OK

      val jsonResponse = contentAsJson(result)

      // None of these lines below should throw if successful.
      (jsonResponse \\ "version") map (_.as[String])
      (jsonResponse \\ "endpoints").map(_ \\ "endpointName").map(_.map(_.as[String]))

      val statuses = (jsonResponse \\ "status") map (_.as[String])
      statuses.find(_ == "ALPHA").size should be(statuses.size) // All instances of status should be ALPHA
      val endpointsEnabledList = (jsonResponse \\ "endpointsEnabled") map (_.as[Boolean])
      endpointsEnabledList.find(_ == false).size should be(endpointsEnabledList.size) // All instances of endpointsEnabled should be false
    }

    "provide yaml documentation" in new Setup {
      val result = documentationController.asset("1.0", "application.yaml")(request)

      status(result) shouldBe OK
      contentAsString(result) should include("openapi: 3.0.3")
    }
  }

  override protected def afterEach(): Unit = {
    wireMockServer.stop()
    wireMockServer.resetMappings()
  }
}

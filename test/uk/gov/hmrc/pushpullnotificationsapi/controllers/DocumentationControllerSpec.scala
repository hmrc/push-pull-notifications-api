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

import scala.concurrent.Future

import org.apache.pekko.stream.Materializer
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.Helpers.{route, _}
import play.api.test.{FakeRequest, Helpers}

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig

class DocumentationControllerSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with BeforeAndAfterEach {

  implicit def mat: Materializer = app.injector.instanceOf[Materializer]
  val mockAppConfig: AppConfig = mock[AppConfig]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AppConfig].to(mockAppConfig))
    .build()

  override def beforeEach(): Unit = {
    reset(mockAppConfig)
  }

  def setUpAppConfig(status: String): Unit = {
    when(mockAppConfig.apiStatus).thenReturn(status)
  }

  "DocumentationController" when {
    "definition" should {
      "return false when status is set to ALPHA" in {
        setUpAppConfig("ALPHA")
        val result = doGet("/api/definition", Map.empty)

        val jsonResult = Helpers.contentAsJson(result)
        (jsonResult \ "api" \ "versions" \ 0 \ "status").as[String] shouldBe "ALPHA"
        (jsonResult \ "api" \ "versions" \ 0 \ "endpointsEnabled").as[Boolean] shouldBe false
      }

      "return true when status is not set to ALPHA" in {
        setUpAppConfig("BETA")
        val result = doGet("/api/definition", Map.empty)

        val jsonResult = Helpers.contentAsJson(result)
        (jsonResult \ "api" \ "versions" \ 0 \ "status").as[String] shouldBe "BETA"
        (jsonResult \ "api" \ "versions" \ 0 \ "endpointsEnabled").as[Boolean] shouldBe true
      }
    }

    "yaml" should {
      "return application.yaml without cmb endpoints" in {
        val result: Future[Result] = doGet("/api/conf/1.0/application.yaml", Map.empty)

        status(result) shouldBe OK
        val stringResult = Helpers.contentAsString(result)

        stringResult should include("summary: Get a list of notifications")
      }

      "return specified file when file is not application.yaml" in {
        val result: Future[Result] = doGet("/api/conf/common/overview.md", Map.empty)
        val textResult = Helpers.contentAsString(result)

        textResult should include("Notifications will be deleted after 30 days.")
      }
    }
  }

  def doGet(uri: String, headers: Map[String, String]): Future[Result] = {
    val fakeRequest = FakeRequest(GET, uri).withHeaders(headers.toSeq: _*)
    route(app, fakeRequest).get
  }
}

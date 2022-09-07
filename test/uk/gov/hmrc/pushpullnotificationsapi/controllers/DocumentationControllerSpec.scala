/*
 * Copyright 2022 HM Revenue & Customs
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

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.Helpers.{route, _}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec

import scala.concurrent.Future

class DocumentationControllerSpec extends AsyncHmrcSpec
  with GuiceOneAppPerSuite with BeforeAndAfterEach {

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]
  val mockAppConfig: AppConfig = mock[AppConfig]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AppConfig].to(mockAppConfig))
    .build()
  override def beforeEach(): Unit = {
   reset(mockAppConfig)
  }

  def setUpAppConfig(status: String): Unit ={
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
      "return application.yaml without cmb endpoints when cmb.enabled is false" in {
        when(mockAppConfig.cmbEnabled).thenReturn(false)
        val result: Future[Result] = doGet("/api/conf/1.0/application.yaml", Map.empty)

        status(result) shouldBe OK
        val stringResult = Helpers.contentAsString(result)

        stringResult should not include ("/cmb/box:")
        stringResult should not include ("/cmb/box/{boxId}:")
      }

      "return yaml from twirl template with cmb endpoints when cmb.enabled is true" in {
        when(mockAppConfig.cmbEnabled).thenReturn(true)
        val result: Future[Result] = doGet("/api/conf/1.0/application.yaml", Map.empty)

        status(result) shouldBe OK
        val stringResult = Helpers.contentAsString(result)

        stringResult should include ("/misc/push-pull-notification/cmb/box")
        stringResult should include ("/misc/push-pull-notification/cmb/box/{boxId}")
      }

      "return specified file when file is not application.yaml" in {
        val result: Future[Result] = doGet("/api/conf/1.0/schemas/acknowledge.json", Map.empty)
        val jsonResult = Helpers.contentAsJson(result)

        (jsonResult \ "title").as[String] shouldBe "Acknowledge a list of notifications"
      }
    }
  }

  def doGet(uri: String, headers: Map[String, String]): Future[Result] = {
    val fakeRequest = FakeRequest(GET, uri).withHeaders(headers.toSeq: _*)
    route(app, fakeRequest).get
  }
}

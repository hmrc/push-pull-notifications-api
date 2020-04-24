/*
 * Copyright 2020 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{reset, verify, when, verifyNoInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.mvc.Results.{InternalServerError, Created}
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_REQUEST, POST, route, _}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.services.TopicsService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import scala.concurrent.ExecutionContext.Implicits.global

class TopicsControllerSpec extends UnitSpec with MockitoSugar
  with GuiceOneAppPerSuite with BeforeAndAfterEach{

  val mockTopicsService: TopicsService = mock[TopicsService]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[TopicsService].to(mockTopicsService))
    .build()

  override def beforeEach(): Unit = {
    reset(mockTopicsService)
  }

  val clientId = "clientid"
  val topicName = "topicName"
  val jsonBody: String =  raw"""{"topicName": "$topicName",
                       |"clientId": "$clientId" }""".stripMargin

  private val validHeaders: Map[String, String] = Map("Content-Type"->"application/json")


  "TopicsController" when {
    "createTopic" should {
        "return 201 when topic successfully created" in {
          when(mockTopicsService.createTopic(any[String], any[String])(any[ExecutionContext])).thenReturn(Future.successful(Created))
         val result = doPost("/topics", validHeaders, jsonBody)
          status(result) should be(CREATED)

          verify(mockTopicsService).createTopic(eqTo(clientId), eqTo(topicName))(any[ExecutionContext])
        }

      "return the service result" in {
        when(mockTopicsService.createTopic(any[String], any[String])(any[ExecutionContext])).thenReturn(Future.successful(InternalServerError))
        val result = doPost("/topics", validHeaders, jsonBody)
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockTopicsService).createTopic(eqTo(clientId), eqTo(topicName))(any[ExecutionContext])
      }

      "return 400 when non JSon payload sent" in {
        val result = doPost("/topics", validHeaders, "xxx")
        status(result) should be(BAD_REQUEST)
        verifyNoInteractions(mockTopicsService)
      }

      "return 422 when Json sent but header not set" in {
        val result = doPost("/topics", Map.empty, "{}")
        status(result) should be(UNPROCESSABLE_ENTITY)
        verifyNoInteractions(mockTopicsService)
      }

      "return 422 when invalid JSon payload sent" in {
        val result = doPost("/topics", validHeaders, "{}")
        status(result) should be(UNPROCESSABLE_ENTITY)
        verifyNoInteractions(mockTopicsService)
      }
    }
  }

  def doPost(uri: String, headers: Map[String, String], bodyValue: String): Future[Result] ={
    val maybeBody: Option[JsValue] =  Try{
      Json.parse(bodyValue)
    } match {
      case Success(value) => Some(value)
      case Failure(_) =>  None
    }

    val fakeRequest =  FakeRequest(POST, uri).withHeaders(headers.toSeq: _*)
    maybeBody
      .fold(route(app, fakeRequest.withBody(bodyValue)).get)(jsonBody => route(app, fakeRequest.withJsonBody(jsonBody)).get)

  }
}

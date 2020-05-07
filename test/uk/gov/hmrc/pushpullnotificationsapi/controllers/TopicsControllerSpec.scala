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

import java.util.UUID

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{reset, verify, verifyNoInteractions, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers.{BAD_REQUEST, POST, route, _}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.ReactiveMongoFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.{DuplicateTopicException, Topic, TopicCreator, UpdateSubscribersRequest}
import uk.gov.hmrc.pushpullnotificationsapi.services.TopicsService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class TopicsControllerSpec extends UnitSpec with MockitoSugar
  with GuiceOneAppPerSuite with BeforeAndAfterEach {

  val mockTopicsService: TopicsService = mock[TopicsService]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[TopicsService].to(mockTopicsService))
    .build()

  override def beforeEach(): Unit = {
    reset(mockTopicsService)
  }

  val clientId: String = "clientid"
  val topicName: String = "topicName"
  val topicId: String = UUID.randomUUID().toString
  val jsonBody: String =
    raw"""{"topicName": "$topicName",
         |"clientId": "$clientId" }""".stripMargin

  private val validHeaders: Map[String, String] = Map("Content-Type" -> "application/json")


  "TopicsController" when {
    "createTopic" should {
      "return 201 when topic successfully created" in {
        when(mockTopicsService.createTopic(any[String], any[String], any[String])(any[ExecutionContext])).thenReturn(Future.successful(()))
        val result = doPost("/topics", validHeaders, jsonBody)
        status(result) should be(CREATED)

        verify(mockTopicsService).createTopic(any[String], eqTo(clientId), eqTo(topicName))(any[ExecutionContext])
      }


      "return 422 when service fails with duplicate topic exception" in {
        when(mockTopicsService.createTopic(any[String], any[String], any[String])(any[ExecutionContext]))
          .thenReturn(Future.failed(DuplicateTopicException("some error")))
        val result = await(doPost("/topics", validHeaders, jsonBody))
        status(result) should be(UNPROCESSABLE_ENTITY)
        contentAsJson(result).toString() shouldBe "{\"code\":\"DUPLICATE_TOPIC\",\"message\":\"some error\"}"

        verify(mockTopicsService).createTopic(any[String], eqTo(clientId), eqTo(topicName))(any[ExecutionContext])
      }

      "return 500 when service fails with any runtime exception" in {
        when(mockTopicsService.createTopic(any[String], any[String], any[String])(any[ExecutionContext]))
          .thenReturn(Future.failed(new RuntimeException("some error")))
        val result = doPost("/topics", validHeaders, jsonBody)
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockTopicsService).createTopic(any[String], eqTo(clientId), eqTo(topicName))(any[ExecutionContext])
      }

      "return 400 when non JSon payload sent" in {
        val result = doPost("/topics", validHeaders, "xxx")
        status(result) should be(BAD_REQUEST)
        verifyNoInteractions(mockTopicsService)
      }


      "return 422 when invalid JSon payload sent" in {
        val result = doPost("/topics", validHeaders, "{}")
        status(result) should be(UNPROCESSABLE_ENTITY)
        verifyNoInteractions(mockTopicsService)
      }
    }

    "getTopicByNameAndClientId" should {

      "return OK and requested topic when it exists" in {

        when(mockTopicsService.getTopicByNameAndClientId(eqTo(topicName), eqTo(clientId))(any[ExecutionContext]))
          .thenReturn(Future.successful(List(Topic(topicId = UUID.randomUUID().toString, topicName = topicName, TopicCreator(clientId)))))

        val result: Result = await(doGet(s"/topics?topicName=$topicName&clientId=$clientId", validHeaders))

        status(result) should be(OK)

        verify(mockTopicsService).getTopicByNameAndClientId(eqTo(topicName), eqTo(clientId))(any[ExecutionContext])
        val bodyVal = Helpers.contentAsString(result)
        val topic = Json.parse(bodyVal).as[Topic]
        topic.subscribers shouldBe empty
      }

      "return NOTFOUND when requested topic does not exist" in {

        when(mockTopicsService.getTopicByNameAndClientId(eqTo(topicName), eqTo(clientId))(any[ExecutionContext]))
          .thenReturn(Future.successful(List.empty))

        val result: Result = await(doGet(s"/topics?topicName=$topicName&clientId=$clientId", validHeaders))

        status(result) should be(NOT_FOUND)

        verify(mockTopicsService).getTopicByNameAndClientId(eqTo(topicName), eqTo(clientId))(any[ExecutionContext])
      }
    }

    "updateSubscribers" should {

      val validUpdateSubscribersRequestJson = "{ \"subscribers\":[" +
        "{\"subscriberId\": \"somesubscriberId\", \"subscriberType\": \"API_PUSH_SUBSCRIBER\", " +
        "\"callBackUrl\":\"someURL\"}" +
        "]}"

      "return 200 when valid request and topic update is successful" in {
        when(mockTopicsService.updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(Topic(topicId = topicId, topicName = topicName, TopicCreator(clientId)))))

        val result: Result = await(doPut(s"/topics/$topicId/subscribers", validHeaders, validUpdateSubscribersRequestJson))
        println(validUpdateSubscribersRequestJson)

        verify(mockTopicsService).updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext])
        status(result) should be(OK)
      }

      "return 404 when valid request and topic update is successful" in {
        when(mockTopicsService.updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext]))
          .thenReturn(Future.successful(None))

        val result = await(doPut(s"/topics/$topicId/subscribers", validHeaders, validUpdateSubscribersRequestJson))

        verify(mockTopicsService).updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext])
        status(result) should be(NOT_FOUND)
      }


      "return 500 when valid request and topic service returns failed future" in {
        when(mockTopicsService.updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext]))
          .thenReturn(Future.failed( new RuntimeException("someError")))

        val result = await(doPut(s"/topics/$topicId/subscribers", validHeaders, validUpdateSubscribersRequestJson))

        verify(mockTopicsService).updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext])
        status(result) should be(INTERNAL_SERVER_ERROR)
      }


      "return 422 when JSON is sent not valid against the requestObject" in {
        when(mockTopicsService.updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(Topic(topicId = topicId, topicName = topicName, TopicCreator(clientId)))))

        val result = doPut(s"/topics/$topicId/subscribers", validHeaders, "{}")

        status(result) should be(UNPROCESSABLE_ENTITY)
      }

      "return 400 when Non JSON payload is sent" in {
        when(mockTopicsService.updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(Topic(topicId = topicId, topicName = topicName, TopicCreator(clientId)))))

        val result = doPut(s"/topics/$topicId/subscribers", validHeaders, "IamNotJson")

        status(result) should be(BAD_REQUEST)
      }

    }
  }

  def doGet(uri: String, headers: Map[String, String]): Future[Result] = {
    val fakeRequest = FakeRequest(GET, uri).withHeaders(headers.toSeq: _*)
    route(app, fakeRequest).get
  }

  def doPost(uri: String, headers: Map[String, String], bodyValue: String): Future[Result] = {
    doPOSTorPUT(uri, headers, bodyValue, POST)
  }

  def doPut(uri: String, headers: Map[String, String], bodyValue: String): Future[Result] = {
    doPOSTorPUT(uri, headers, bodyValue, PUT)
  }

  private def doPOSTorPUT(uri: String, headers: Map[String, String], bodyValue: String, method: String): Future[Result] = {
    val maybeBody: Option[JsValue] = Try {
      Json.parse(bodyValue)
    } match {
      case Success(value) => Some(value)
      case Failure(_) => None
    }

    val fakeRequest = FakeRequest(method, uri).withHeaders(headers.toSeq: _*)
    maybeBody
      .fold(route(app, fakeRequest.withBody(bodyValue)).get)(jsonBody => route(app, fakeRequest.withJsonBody(jsonBody)).get)

  }
}

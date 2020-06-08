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

import org.mockito.ArgumentMatchersSugar
import org.mockito.Mockito.{reset, verify, verifyNoInteractions, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers.{BAD_REQUEST, route, _}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders.AuthAction
import uk.gov.hmrc.pushpullnotificationsapi.models.ReactiveMongoFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.services.TopicsService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class TopicsControllerSpec extends UnitSpec with MockitoSugar with ArgumentMatchersSugar
  with GuiceOneAppPerSuite with BeforeAndAfterEach {

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

  val mockTopicsService: TopicsService = mock[TopicsService]
  val mockAppConfig: AppConfig = mock[AppConfig]
  val mockAuthAction: AuthAction = mock[AuthAction]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[TopicsService].to(mockTopicsService))
    .overrides(bind[AuthAction].to(mockAuthAction))
    .overrides(bind[AppConfig].to(mockAppConfig))
    .build()

  override def beforeEach(): Unit = {
    reset(mockTopicsService, mockAppConfig)
  }

  private def setUpAppConfig(userAgents: List[String]): Unit = {
    when(mockAppConfig.whitelistedUserAgentList).thenReturn(userAgents)
  }

  val clientIdStr: String = UUID.randomUUID().toString
  val clientId: ClientId = ClientId(clientIdStr)
  val topicName: String = "topicName"

  val topicIdstr: String = UUID.randomUUID().toString
  val topicId: TopicId = TopicId(UUID.fromString(topicIdstr))
  val jsonBody: String =
    raw"""{"topicName": "$topicName",
         |"clientId": "$clientIdStr" }""".stripMargin

  private val validHeadersWithValidUserAgent: Map[String, String] = Map("Content-Type" -> "application/json", "User-Agent" -> "api-subscription-fields")
  private val validHeadersWithInValidUserAgent: Map[String, String] = Map("Content-Type" -> "application/json", "User-Agent" -> "some-other-service")

  private val validHeaders: Map[String, String] = Map("Content-Type" -> "application/json")


  "TopicsController" when {
    "createTopic" should {
      "return 201 and topicId when topic successfully created" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockTopicsService.createTopic(any[TopicId], any[ClientId], any[String])(any[ExecutionContext]))
          .thenReturn(Future.successful(Right(TopicServiceCreateSuccessResult(topicId))))
        val result = await(doPut("/topics", validHeadersWithValidUserAgent, jsonBody))
        status(result) should be(CREATED)
        val expectedBodyStr = s"""{"topicId":"${topicId.value}"}"""
        jsonBodyOf(result) should be (Json.parse(expectedBodyStr))

        verify(mockTopicsService).createTopic(any[TopicId], eqTo(clientId), eqTo(topicName))(any[ExecutionContext])
      }

      "return 200 and topicId when topic already exists" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockTopicsService.createTopic(any[TopicId], any[ClientId], any[String])(any[ExecutionContext]))
          .thenReturn(Future.successful(Right(TopicServiceCreateRetrievedSuccessResult(topicId))))
        val result = await(doPut("/topics", validHeadersWithValidUserAgent, jsonBody))
        status(result) should be(OK)
        val expectedBodyStr = s"""{"topicId":"${topicId.value}"}"""
        jsonBodyOf(result) should be(Json.parse(expectedBodyStr))

        verify(mockTopicsService).createTopic(any[TopicId], eqTo(clientId), eqTo(topicName))(any[ExecutionContext])
      }


      "return 422 when Left returned from Topic Service" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockTopicsService.createTopic(any[TopicId], any[ClientId], any[String])(any[ExecutionContext]))
          .thenReturn(Future.successful(Left(TopicServiceCreateFailedResult(s"Topic with name :$topicName already exists for cleintId: $clientId but unable to retrieve"))))
        val result = await(doPut("/topics", validHeadersWithValidUserAgent, jsonBody))
        status(result) should be(UNPROCESSABLE_ENTITY)

        verify(mockTopicsService).createTopic(any[TopicId], eqTo(clientId), eqTo(topicName))(any[ExecutionContext])
      }

      "return 400 when useragent config is empty" in {
        setUpAppConfig(List.empty)
        val result = doPut("/topics", validHeadersWithValidUserAgent, jsonBody)
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockTopicsService)
      }


      "return 401 when invalid useragent provided" in {
        setUpAppConfig(List("api-subscription-fields"))
        val result = doPut("/topics", validHeadersWithInValidUserAgent, jsonBody)
        status(result) should be(UNAUTHORIZED)

        verifyNoInteractions(mockTopicsService)
      }

      "return 401 when no useragent header provided" in {
        setUpAppConfig(List("api-subscription-fields"))
        val result = doPut("/topics", validHeaders, jsonBody)
        status(result) should be(UNAUTHORIZED)

        verifyNoInteractions(mockTopicsService)
      }


      "return 500 when service fails with any runtime exception" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockTopicsService.createTopic(any[TopicId], any[ClientId], any[String])(any[ExecutionContext]))
          .thenReturn(Future.failed(new RuntimeException("some error")))
        val result = doPut("/topics", validHeadersWithValidUserAgent, jsonBody)
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockTopicsService).createTopic(any[TopicId], eqTo(clientId), eqTo(topicName))(any[ExecutionContext])
      }

      "return 400 when non JSon payload sent" in {
        setUpAppConfig(List("api-subscription-fields"))
        val result = doPut("/topics", validHeaders, "xxx")
        status(result) should be(BAD_REQUEST)
        verifyNoInteractions(mockTopicsService)
      }


      "return 422 when invalid JSon payload sent" in {
        setUpAppConfig(List("api-subscription-fields"))
        val result = doPut("/topics", validHeadersWithValidUserAgent, "{}")
        status(result) should be(UNPROCESSABLE_ENTITY)
        verifyNoInteractions(mockTopicsService)
      }
    }

    "getTopicByNameAndClientId" should {

      "return OK and requested topic when it exists" in {

        when(mockTopicsService.getTopicByNameAndClientId(eqTo(topicName), eqTo(clientId))(any[ExecutionContext]))
          .thenReturn(Future.successful(List(Topic(topicId = TopicId(UUID.randomUUID()), topicName = topicName, TopicCreator(clientId)))))

        val result: Result = await(doGet(s"/topics?topicName=$topicName&clientId=${clientId.value}", validHeaders))

        status(result) should be(OK)

        verify(mockTopicsService).getTopicByNameAndClientId(eqTo(topicName), eqTo(clientId))(any[ExecutionContext])
        val bodyVal = Helpers.contentAsString(result)
        val topic = Json.parse(bodyVal).as[Topic]
        topic.subscribers shouldBe empty
      }

      "return NOTFOUND when requested topic does not exist" in {

        when(mockTopicsService.getTopicByNameAndClientId(eqTo(topicName), eqTo(clientId))(any[ExecutionContext]))
          .thenReturn(Future.successful(List.empty))

        val result: Result = await(doGet(s"/topics?topicName=$topicName&clientId=${clientId.value}", validHeaders))

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

        val result: Result = await(doPut(s"/topics/${topicId.raw}/subscribers", validHeaders, validUpdateSubscribersRequestJson))
        println(validUpdateSubscribersRequestJson)
        status(result) should be(OK)

        verify(mockTopicsService).updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext])

      }

      "return 404 when valid request and topic update is successful" in {
        when(mockTopicsService.updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext]))
          .thenReturn(Future.successful(None))

        val result = await(doPut(s"/topics/${topicId.raw}/subscribers", validHeaders, validUpdateSubscribersRequestJson))
        status(result) should be(NOT_FOUND)

        verify(mockTopicsService).updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext])

      }


      "return 500 when valid request and topic service returns failed future" in {
        when(mockTopicsService.updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext]))
          .thenReturn(Future.failed(new RuntimeException("someError")))

        val result = await(doPut(s"/topics/${topicId.raw}/subscribers", validHeaders, validUpdateSubscribersRequestJson))
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockTopicsService).updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext])

      }


      "return 422 when JSON is sent not valid against the requestObject" in {
        when(mockTopicsService.updateSubscribers(eqTo(topicId), any[UpdateSubscribersRequest])(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(Topic(topicId = topicId, topicName = topicName, TopicCreator(clientId)))))

        val result = doPut(s"/topics/${topicId.raw}/subscribers", validHeaders, "{}")

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


  def doPut(uri: String, headers: Map[String, String], bodyValue: String): Future[Result] = {
    doPUT(uri, headers, bodyValue)
  }

  private def doPUT(uri: String, headers: Map[String, String], bodyValue: String): Future[Result] = {
    val maybeBody: Option[JsValue] = Try {
      Json.parse(bodyValue)
    } match {
      case Success(value) => Some(value)
      case Failure(_) => None
    }

    val fakeRequest = FakeRequest(PUT, uri).withHeaders(headers.toSeq: _*)
    maybeBody
      .fold(route(app, fakeRequest.withBody(bodyValue)).get)(jsonBody => route(app, fakeRequest.withJsonBody(jsonBody)).get)

  }
}

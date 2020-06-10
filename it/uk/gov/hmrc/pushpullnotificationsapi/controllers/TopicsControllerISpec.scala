package uk.gov.hmrc.pushpullnotificationsapi.controllers

import java.util.UUID

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{BAD_REQUEST, CREATED, NOT_FOUND, OK, UNAUTHORIZED, UNSUPPORTED_MEDIA_TYPE}
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.Topic
import uk.gov.hmrc.pushpullnotificationsapi.repository.TopicsRepository
import uk.gov.hmrc.pushpullnotificationsapi.support.{MongoApp, ServerBaseISpec}

import scala.concurrent.ExecutionContext.Implicits.global

class TopicsControllerISpec extends ServerBaseISpec with BeforeAndAfterEach with MongoApp {
  this: Suite with ServerProvider =>

  def repo: TopicsRepository =
    app.injector.instanceOf[TopicsRepository]

  override def beforeEach(): Unit ={
    super.beforeEach()
    dropMongoDb()
    await(repo.ensureIndexes)
  }

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled"                 -> true,
        "auditing.enabled"                -> true,
        "auditing.consumer.baseUri.host"  -> wireMockHost,
        "auditing.consumer.baseUri.port"  -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  val url = s"http://localhost:$port"


  val topicName = "mytopicName"
  val clientId = "someClientId"
  val createTopicJsonBody =raw"""{"clientId": "$clientId", "topicName": "$topicName"}"""
  val createTopic2JsonBody =raw"""{"clientId": "zzzzzzzzzz", "topicName": "bbyybybyb"}"""

  val updateSubcribersJsonBodyWithIds: String = raw"""{ "subscribers":[{
                                             |     "subscriberType": "API_PUSH_SUBSCRIBER",
                                             |     "callBackUrl":"somepath/firstOne",
                                             |     "subscriberId": "74d3ef1e-9b6f-4e75-897d-217cc270140f"
                                             |   }]
                                             |}
                                             |""".stripMargin

  val validHeaders = List("Content-Type" -> "application/json",  "User-Agent" -> "api-subscription-fields")

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def doPut(jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/topics")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def doPut(topicId:String, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/topics/$topicId/subscribers")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def doGet(topicName:String, clientId: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/topics?topicName=$topicName&clientId=$clientId")
      .withHttpHeaders(headers: _*)
      .get
      .futureValue

  // need to clean down mongo then run two




  "TopicsController" when {

    "POST /topics" should {
      "respond with 201 when topic created" in {
        val result = doPut(createTopicJsonBody, validHeaders)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 200 with topic ID  when topic already exists" in {
        val result1 = doPut(createTopicJsonBody, validHeaders)
        validateStringIsUUID(result1.body)

        val result2 = doPut(createTopicJsonBody, validHeaders)
        result2.status shouldBe OK
        validateStringIsUUID(result2.body)
        result2.body shouldBe result1.body
      }

      "respond with 201 when two topics are created" in {
        val result = doPut(createTopicJsonBody, validHeaders)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)

        val result2 = doPut(createTopic2JsonBody, validHeaders)
        result2.status shouldBe CREATED
        validateStringIsUUID(result2.body)
      }

      "respond with 400 when NonJson is sent" in {
        val result = doPut("nonJsonPayload", validHeaders)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 400 when invalid Json is sent" in {
        val result = doPut("{}", validHeaders)
        result.status shouldBe BAD_REQUEST
        result.body.contains("INVALID_REQUEST_PAYLOAD") shouldBe true
      }

      "respond with 415 when request content Type headers are empty" in {
        val result = doPut("{}", List("someHeader" -> "someValue"))
        result.status shouldBe UNSUPPORTED_MEDIA_TYPE
      }

      "respond with 415 when request content Type header is not JSON " in {
        val result = doPut("{}",  List("Content-Type" -> "application/xml"))
        result.status shouldBe UNSUPPORTED_MEDIA_TYPE
      }

      "respond with 401 when UserAgent is not sent " in {
        val result = doPut("{}",  List("Content-Type" -> "application/json"))
        result.status shouldBe UNAUTHORIZED
      }

      "respond with 401 when UserAgent is not in whitelist" in {
        val result = doPut("{}",  List("Content-Type" -> "application/json", "User-Agent"->"not-a-known-one"))
        result.status shouldBe UNAUTHORIZED
      }
    }
  }

  "GET /topics?topicName=someName&clientId=someClientid" should {
    "respond with 200 and topic in body when exists" in {
      val result = doPut(createTopicJsonBody, validHeaders)
      result.status shouldBe CREATED
      validateStringIsUUID(result.body)

      val result2 = doGet(topicName, clientId, validHeaders)
      result2.status shouldBe OK

      val topic = Json.parse(result2.body).as[Topic]
      topic.topicName shouldBe topicName
      topic.topicCreator.clientId.value shouldBe clientId

    }

    "respond with 404 when topic does not exists" in {
      val result2 = doGet(topicName, clientId, validHeaders)
      result2.status shouldBe NOT_FOUND

    }
  }

  "PUT /topics/{topicId}/subscribers" should {

    "return 200 and update topic successfully when topic exists" in {

      val createdTopic = createTopicAndCheckExistsWithNoSubscribers()

      val updateResult = doPut(createdTopic.topicId.raw, updateSubcribersJsonBodyWithIds, validHeaders)
      updateResult.status shouldBe OK

      val updatedTopic = Json.parse(updateResult.body).as[Topic]
      updatedTopic.subscribers.size shouldBe 1

    }

    "return 404 when topic does not exist" in {
      val updateResult = doPut(UUID.randomUUID().toString, updateSubcribersJsonBodyWithIds, validHeaders)
      updateResult.status shouldBe NOT_FOUND
    }

    "return 400 when requestBody is not a valid payload" in {
      val updateResult = doPut(UUID.randomUUID().toString, "{}", validHeaders)
      updateResult.status shouldBe BAD_REQUEST
    }
  }

  private def createTopicAndCheckExistsWithNoSubscribers(): Topic ={
    val result = doPut(createTopicJsonBody, validHeaders)
    result.status shouldBe CREATED
    validateStringIsUUID(result.body)

    val result2 = doGet(topicName, clientId, validHeaders)
    result2.status shouldBe OK
    val topic = Json.parse(result2.body).as[Topic]
    topic.subscribers.size shouldBe 0
    topic
  }
}

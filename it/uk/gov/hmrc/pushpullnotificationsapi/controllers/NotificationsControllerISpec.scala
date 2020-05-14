package uk.gov.hmrc.pushpullnotificationsapi.controllers

import java.util.UUID

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{BAD_REQUEST, CREATED, NOT_FOUND, UNPROCESSABLE_ENTITY}
import uk.gov.hmrc.pushpullnotificationsapi.models.Topic
import uk.gov.hmrc.pushpullnotificationsapi.repository.{NotificationsRepository, TopicsRepository}
import uk.gov.hmrc.pushpullnotificationsapi.support.{MongoApp, ServerBaseISpec}

import scala.concurrent.ExecutionContext.Implicits.global

class NotificationsControllerISpec extends ServerBaseISpec with BeforeAndAfterEach with MongoApp {
  this: Suite with ServerProvider =>

  def topicsRepo:  TopicsRepository= app.injector.instanceOf[TopicsRepository]
  def notificationRepo: NotificationsRepository = app.injector.instanceOf[NotificationsRepository]

  override def beforeEach(): Unit ={
    super.beforeEach()
    dropMongoDb()
    await(topicsRepo.ensureIndexes)
    await(notificationRepo.ensureIndexes)
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

  val validHeadersJson: (String, String) = "Content-Type" -> "application/json"
  val validHeadersXml: (String, String) = "Content-Type" -> "application/xml"

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def doPost(urlString: String, jsonBody: String, headers: (String, String)): WSResponse =
    wsClient
      .url(urlString)
      .withHttpHeaders(headers)
      .post(jsonBody)
      .futureValue

  // need to clean down mongo then run two

  def createTopicAndReturn(): Topic = {
    val result = doPost( s"$url/topics", createTopicJsonBody, validHeadersJson)
    result.status shouldBe CREATED
    await(topicsRepo.findAll().head)
  }

  "NotificationsController" when {

    "POST /notification/topics/[topicId]" should {
      "respond with 201 when notification created for valid json and json content type" in {
       val topic =  createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${topic.topicId}", "{}", validHeadersJson)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 201 when notification created for valid xml and xml content type" in {
        val topic = createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${topic.topicId}", "<somNode/>", validHeadersXml)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 400 when for valid xml and but json content type" in {
        val topic = createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${topic.topicId}", "<somNode/>", validHeadersJson)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 400 when for valid json and but xml content type" in {
        val topic = createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${topic.topicId}", "{}", validHeadersXml)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 400 when unknown content type sent in request" in {
        val topic = createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${topic.topicId}", "{}", "ContentType" -> "text/plain")
        result.status shouldBe BAD_REQUEST
      }

      "respond with 404 when unknown / non existent topic id sent" in {
        createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${UUID.randomUUID().toString}", "{}", validHeadersJson)
        result.status shouldBe NOT_FOUND
      }

      "respond with 422 when attempt to create duplicate notification" in {
        val topic =  createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${topic.topicId}", "{}", validHeadersJson)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)

        val result2 = doPost( s"$url/notifications/topics/${topic.topicId}", "{}", validHeadersJson)
        result2.status shouldBe UNPROCESSABLE_ENTITY
      }


    }

  }


}

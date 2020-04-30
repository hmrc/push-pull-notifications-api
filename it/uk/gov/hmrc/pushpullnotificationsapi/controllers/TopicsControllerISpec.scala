package uk.gov.hmrc.pushpullnotificationsapi.controllers

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.pushpullnotificationsapi.repository.TopicsRepository
import uk.gov.hmrc.pushpullnotificationsapi.support.{MongoApp, ServerBaseISpec}
import play.api.test.Helpers.{UNPROCESSABLE_ENTITY, CREATED, UNSUPPORTED_MEDIA_TYPE, BAD_REQUEST, NO_CONTENT}
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

  val createTopicJsonBody =raw"""{"clientId": "akjhjkhjshjkhksaih", "topicName": "iuiuiuojo"}"""
  val createTopic2JsonBody =raw"""{"clientId": "zzzzzzzzzz", "topicName": "bbyybybyb"}"""

  val validHeaders: (String, String) = "Content-Type" -> "application/json"

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def doPost(jsonBody: String, headers: (String, String)): WSResponse =
    wsClient
      .url(s"$url/topics")
      .withHttpHeaders(headers)
      .post(jsonBody)
      .futureValue

  // need to clean down mongo then run two

  "TopicsController" when {

    "GET /topics" should {
      "respond with 201 when topic created" in {
        val result = doPost(createTopicJsonBody, validHeaders)
        result.status shouldBe CREATED
      }

      "respond with 422 when topic already exists" in {
        doPost(createTopicJsonBody, validHeaders)

        val result2 = doPost(createTopicJsonBody, validHeaders)
        result2.status shouldBe UNPROCESSABLE_ENTITY
        result2.body.contains("DUPLICATE_TOPIC") shouldBe true
      }

      "respond with 201 when two topics are created" in {
        val result = doPost(createTopicJsonBody, validHeaders)
        result.status shouldBe CREATED

        val result2 = doPost(createTopic2JsonBody, validHeaders)
        result2.status shouldBe CREATED
      }

      "respond with 400 when NonJson is sent" in {
        val result = doPost("nonJsonPayload", validHeaders)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 422 when invalid Json is sent" in {
        val result = doPost("{}", validHeaders)
        result.status shouldBe UNPROCESSABLE_ENTITY
        result.body.contains("INVALID_REQUEST_PAYLOAD") shouldBe true
      }

      "respond with 415 when request content Type headers are empty" in {
        val result = doPost("{}", "someHeader" -> "someValue")
        result.status shouldBe UNSUPPORTED_MEDIA_TYPE
      }

      "respond with 415 when request content Type header is not JSON " in {
        val result = doPost("{}", "Content-Type" -> "application/xml")
        result.status shouldBe UNSUPPORTED_MEDIA_TYPE
      }
    }
  }
}

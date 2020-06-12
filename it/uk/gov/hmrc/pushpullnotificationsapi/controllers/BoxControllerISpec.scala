package uk.gov.hmrc.pushpullnotificationsapi.controllers

import java.util.UUID

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{BAD_REQUEST, CREATED, NOT_FOUND, OK, UNAUTHORIZED, UNSUPPORTED_MEDIA_TYPE, FORBIDDEN}
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.Box
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository
import uk.gov.hmrc.pushpullnotificationsapi.support.{MongoApp, ServerBaseISpec}

import scala.concurrent.ExecutionContext.Implicits.global

class BoxControllerISpec extends ServerBaseISpec with BeforeAndAfterEach with MongoApp {
  this: Suite with ServerProvider =>

  def repo: BoxRepository =
    app.injector.instanceOf[BoxRepository]

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


  val boxName = "myBoxName"
  val clientId = "someClientId"
  val createBoxJsonBody =raw"""{"clientId": "$clientId", "boxName": "$boxName"}"""
  val createBox2JsonBody =raw"""{"clientId": "zzzzzzzzzz", "boxName": "bbyybybyb"}"""

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
      .url(s"$url/box")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def doPut(boxId:String, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box/$boxId/subscribers")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def doGet(boxName:String, clientId: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box?boxName=$boxName&clientId=$clientId")
      .withHttpHeaders(headers: _*)
      .get
      .futureValue

  // need to clean down mongo then run two




  "BoxController" when {

    "POST /box" should {
      "respond with 201 when box created" in {
        val result = doPut(createBoxJsonBody, validHeaders)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 200 with box ID  when box already exists" in {
        val result1 = doPut(createBoxJsonBody, validHeaders)
        validateStringIsUUID(result1.body)

        val result2 = doPut(createBoxJsonBody, validHeaders)
        result2.status shouldBe OK
        validateStringIsUUID(result2.body)
        result2.body shouldBe result1.body
      }

      "respond with 201 when two boxs are created" in {
        val result = doPut(createBoxJsonBody, validHeaders)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)

        val result2 = doPut(createBox2JsonBody, validHeaders)
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

      "respond with 403 when UserAgent is not sent " in {
        val result = doPut("{}",  List("Content-Type" -> "application/json"))
        result.status shouldBe FORBIDDEN
      }

      "respond with 403 when UserAgent is not in whitelist" in {
        val result = doPut("{}",  List("Content-Type" -> "application/json", "User-Agent"->"not-a-known-one"))
        result.status shouldBe FORBIDDEN
      }
    }
  }

  "GET /box?boxName=someName&clientId=someClientid" should {
    "respond with 200 and box in body when exists" in {
      val result = doPut(createBoxJsonBody, validHeaders)
      result.status shouldBe CREATED
      validateStringIsUUID(result.body)

      val result2 = doGet(boxName, clientId, validHeaders)
      result2.status shouldBe OK

      val box = Json.parse(result2.body).as[Box]
      box.boxName shouldBe boxName
      box.boxCreator.clientId.value shouldBe clientId

    }

    "respond with 404 when box does not exists" in {
      val result2 = doGet(boxName, clientId, validHeaders)
      result2.status shouldBe NOT_FOUND

    }
  }

  "PUT /box/{boxId}/subscribers" should {

    "return 200 and update box successfully when box exists" in {

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = doPut(createdBox.boxId.raw, updateSubcribersJsonBodyWithIds, validHeaders)
      updateResult.status shouldBe OK

      val updatedBox = Json.parse(updateResult.body).as[Box]
      updatedBox.subscribers.size shouldBe 1

    }

    "return 404 when box does not exist" in {
      val updateResult = doPut(UUID.randomUUID().toString, updateSubcribersJsonBodyWithIds, validHeaders)
      updateResult.status shouldBe NOT_FOUND
    }

    "return 400 when requestBody is not a valid payload" in {
      val updateResult = doPut(UUID.randomUUID().toString, "{}", validHeaders)
      updateResult.status shouldBe BAD_REQUEST
    }
  }

  private def createBoxAndCheckExistsWithNoSubscribers(): Box ={
    val result = doPut(createBoxJsonBody, validHeaders)
    result.status shouldBe CREATED
    validateStringIsUUID(result.body)

    val result2 = doGet(boxName, clientId, validHeaders)
    result2.status shouldBe OK
    val box = Json.parse(result2.body).as[Box]
    box.subscribers.size shouldBe 0
    box
  }
}

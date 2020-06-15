package uk.gov.hmrc.pushpullnotificationsapi.controllers

import java.util.UUID

import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Format
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{ACCEPT, BAD_REQUEST, CREATED, FORBIDDEN, NOT_FOUND, OK, UNAUTHORIZED}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, BoxId}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{BoxRepository, NotificationsRepository}
import uk.gov.hmrc.pushpullnotificationsapi.support.{AuditService, AuthService, MongoApp, ServerBaseISpec}

import scala.concurrent.ExecutionContext.Implicits.global

class NotificationsControllerISpec extends ServerBaseISpec with BeforeAndAfterEach with MongoApp with AuthService with AuditService{
  this: Suite with ServerProvider =>
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  def boxRepository:  BoxRepository= app.injector.instanceOf[BoxRepository]
  def notificationRepo: NotificationsRepository = app.injector.instanceOf[NotificationsRepository]

  override def beforeEach(): Unit ={
    super.beforeEach()
    dropMongoDb()
    primeAuditService()
    await(boxRepository.ensureIndexes)
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

  val boxName = "myboxName"
  val clientId = "someClientId"
  val createBoxJsonBody =raw"""{"clientId": "$clientId", "boxName": "$boxName"}"""
  val createBox2JsonBody =raw"""{"clientId": "zzzzzzzzzz", "boxName": "bbyybybyb"}"""

  val updateSubscribersJsonBodyWithIds: String = raw"""{ "subscribers":[{
                                             |     "subscriberType": "API_PUSH_SUBSCRIBER",
                                             |     "callBackUrl":"somepath/firstOne",
                                             |     "subscriberId": "74d3ef1e-9b6f-4e75-897d-217cc270140f"
                                             |   }]
                                             |}
                                             |""".stripMargin

  val acceptHeader: (String, String) = ACCEPT -> "application/vnd.hmrc.1.0+json"
  val validHeadersJson = List(acceptHeader, "Content-Type" -> "application/json",  "User-Agent" -> "api-subscription-fields")
  val validHeadersJsonWithNoUserAgent = List(acceptHeader, "Content-Type" -> "application/json")
  val validHeadersXml = List(acceptHeader, "Content-Type" -> "application/xml",  "User-Agent" -> "api-subscription-fields")

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def doPost(urlString: String, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(urlString)
      .withHttpHeaders(headers: _*)
      .post(jsonBody)
      .futureValue


  def doPut(urlString: String, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(urlString)
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def doGet(urlString: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(urlString)
      .withHttpHeaders(headers: _*)
      .get
      .futureValue
  // need to clean down mongo then run two

  def createBoxAndReturn(): Box = {
    val result = doPut( s"$url/box", createBoxJsonBody, validHeadersJson)
    result.status shouldBe CREATED
    await(boxRepository.findAll().head)
  }

  def createNotifications(boxId: BoxId, numberToCreate: Int): Unit ={
    for(_ <- 0 until numberToCreate){
      val result = doPost( s"$url/box/${boxId.raw}/notifications", "{}", validHeadersJson)
      result.status shouldBe CREATED
    }


  }
  "NotificationsController" when {

    "POST /box/[boxId]/notifications" should {
      "respond with 201 when notification created for valid json and json content type" in {
       val box =  createBoxAndReturn()
        val result = doPost( s"$url/box/${box.boxId.raw}/notifications", "{}", validHeadersJson)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 201 when notification created for valid xml and xml content type" in {
        val box = createBoxAndReturn()
        val result = doPost( s"$url/box/${box.boxId.raw}/notifications", "<somNode/>", validHeadersXml)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 403 when no useragent sent in request" in {
        val box = createBoxAndReturn()
        val result = doPost( s"$url/box/${box.boxId.raw}/notifications", "{}", List("ContentType" -> "text/plain"))
        result.status shouldBe FORBIDDEN
      }

      "respond with 403 when non whitelisted user agent sent in request" in {
        val box = createBoxAndReturn()
        val result = doPost( s"$url/box/${box.boxId.raw}/notifications", "{}", List("ContentType" -> "text/plain", "User-Agent" -> "non-whitelisted-agent"))
        result.status shouldBe FORBIDDEN
      }



      "respond with 400 when for valid xml and but json content type" in {
        val box = createBoxAndReturn()
        val result = doPost( s"$url/box/${box.boxId.raw}/notifications", "<somNode/>", validHeadersJson)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 400 when for valid json and but xml content type" in {
        val box = createBoxAndReturn()
        val result = doPost( s"$url/box/${box.boxId.raw}/notifications", "{}", validHeadersXml)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 400 when unknown content type sent in request" in {
        val box = createBoxAndReturn()
        val result = doPost( s"$url/box/${box.boxId.raw}/notifications", "{}", List("ContentType" -> "text/plain",   "User-Agent" -> "api-subscription-fields"))
        result.status shouldBe BAD_REQUEST
      }

      "respond with 404 when unknown / non existent box id sent" in {
        createBoxAndReturn()
        val result = doPost( s"$url/box/${UUID.randomUUID().toString}notifications/", "{}", validHeadersJson)
        result.status shouldBe NOT_FOUND
      }
    }

    "GET /box/[boxId]/notifications" should {
      "respond with 201 when notification created for valid json and json content type" in {
        primeAuthServiceSuccess(clientId,"{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box =  createBoxAndReturn()
        createNotifications(box.boxId, 4)
        val result: WSResponse = doGet( s"$url/box/${box.boxId.raw}/notifications", validHeadersJson)
        result.status shouldBe OK
      }

      "respond with 401 on create when clientId returned from auth does not match" in {
        primeAuthServiceSuccess("UnknownClientId","{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box =  createBoxAndReturn()
        createNotifications(box.boxId, 4)
        val result: WSResponse = doGet( s"$url/box/${box.boxId.raw}/notifications", validHeadersJson)
        result.status shouldBe UNAUTHORIZED
      }

      "respond with 401 on create when no clientID in response from auth" in {
        primeAuthServiceNoCLientId("{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box =  createBoxAndReturn()
        createNotifications(box.boxId, 4)
        val result: WSResponse = doGet( s"$url/box/${box.boxId.raw}/notifications", validHeadersJson)
        result.status shouldBe UNAUTHORIZED
      }
      "respond with 401 when authorisation fails" in {
        primeAuthServiceFail()
        val box =  createBoxAndReturn()
        createNotifications(box.boxId, 4)
        val result: WSResponse = doGet( s"$url/box/${box.boxId.raw}/notifications", validHeadersJson)
        result.status shouldBe UNAUTHORIZED
      }
    }
  }
}

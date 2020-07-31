package uk.gov.hmrc.pushpullnotificationsapi.controllers

import java.util.UUID

import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.http.HeaderNames.{CONTENT_TYPE, USER_AGENT}
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Format, JsSuccess, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{ACCEPT, BAD_REQUEST, CREATED, FORBIDDEN, NOT_FOUND, NO_CONTENT, OK, UNAUTHORIZED, UNSUPPORTED_MEDIA_TYPE}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models.RequestFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.{AcknowledgeNotificationsRequest, Box, BoxId, CreateNotificationResponse}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{BoxRepository, NotificationsRepository}
import uk.gov.hmrc.pushpullnotificationsapi.support._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

class NotificationsControllerISpec extends ServerBaseISpec with BeforeAndAfterEach with MongoApp with AuthService with AuditService with PushGatewayService {
  this: Suite with ServerProvider =>
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats

  def boxRepository: BoxRepository = app.injector.instanceOf[BoxRepository]

  def notificationRepo: NotificationsRepository = app.injector.instanceOf[NotificationsRepository]

  override def beforeEach(): Unit = {
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
        "microservice.services.push-pull-notifications-gateway.port" -> wireMockPort,
        "microservice.services.push-pull-notifications-gateway.authorizationKey" -> "iampushpullapi",
        "metrics.enabled" -> true,
        "auditing.enabled" -> true,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  val url = s"http://localhost:$port"

  val boxName = "myboxName"
  val clientId = "someClientId"
  val createBoxJsonBody = raw"""{"clientId": "$clientId", "boxName": "$boxName"}"""
  val createBox2JsonBody = raw"""{"clientId": "zzzzzzzzzz", "boxName": "bbyybybyb"}"""

  val updateSubscriberJsonBodyWithIds: String =
    raw"""{ "subscriber":
         |  {
         |     "subscriberType": "API_PUSH_SUBSCRIBER",
         |     "callBackUrl":"somepath/firstOne"
         |  }
         |}
         |""".stripMargin

  val validConnectorJsonBody: String =
    raw"""{
         |   "destinationUrl":"https://somedomain.com/post-handler",
         |   "forwardedHeaders": [
         |      {"key": "Content-Type", "value": "application/json"},
         |      {"key": "User-Agent", "value": "header-2-value"}
         |   ],
         |   "payload":"{}"
         |}
         |""".stripMargin

  val acceptHeader: (String, String) = ACCEPT -> "application/vnd.hmrc.1.0+json"
  val validHeadersJson = List(acceptHeader, CONTENT_TYPE -> "application/json", USER_AGENT -> "api-subscription-fields")
  val validHeadersJsonWithNoUserAgent = List(acceptHeader, CONTENT_TYPE -> "application/json")
  val validHeadersXml = List(acceptHeader, CONTENT_TYPE -> "application/xml", USER_AGENT -> "api-subscription-fields")

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

  def createBoxAndReturn(): Box = {
    val result = doPut(s"$url/box", createBoxJsonBody, validHeadersJson)
    result.status shouldBe CREATED
    await(boxRepository.findAll().head)
  }

  def createBoxWithSubscribersAndReturn(): Box = {
    val result: WSResponse = doPut(s"$url/box", createBoxJsonBody, validHeadersJson)
    result.status shouldBe CREATED
    val boxId = (Json.parse(result.body) \ "boxId").as[String]

    val updateResult = doPut(s"$url/box/$boxId/subscriber", updateSubscriberJsonBodyWithIds, validHeadersJson)
    updateResult.status shouldBe OK

    await(boxRepository.findAll().head)
  }

  def createNotifications(boxId: BoxId, numberToCreate: Int): List[String] = {
    var notifications: mutable.MutableList[String] = mutable.MutableList[String]()
    for (_ <- 0 until numberToCreate) {
      val result = doPost(s"$url/box/${boxId.raw}/notifications", "{}", validHeadersJson)
      result.status shouldBe CREATED
      notifications += result.body
    }
    List() ++ notifications

  }

  "NotificationsController" when {

    "POST /box/[boxId]/notifications" should {
      "respond with 201 when notification created for valid json and json content type with push subscriber" in {
        primeGatewayServiceWithBody(Status.OK)
        val box = createBoxWithSubscribersAndReturn()
        val result = doPost(s"$url/box/${box.boxId.raw}/notifications", "{}", validHeadersJson)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 201 when notification created for valid json and json content type with no subscriber" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.raw}/notifications", "{}", validHeadersJson)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 201 when notification created for valid xml and xml content type with push subscriber" in {
        primeGatewayServiceWithBody(Status.OK)
        val box = createBoxWithSubscribersAndReturn()
        val result = doPost(s"$url/box/${box.boxId.raw}/notifications", "<somNode/>", validHeadersXml)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 201 when notification created for valid xml and xml content type with no subscribers" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.raw}/notifications", "<somNode/>", validHeadersXml)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 201 when notification created for valid xml and xml content type even if push fails bad request" in {
        primeGatewayServiceWithBody(Status.BAD_REQUEST)
        val box = createBoxWithSubscribersAndReturn()
        val result = doPost(s"$url/box/${box.boxId.raw}/notifications", "<somNode/>", validHeadersXml)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 201 when notification created for valid xml and xml content type even if push fails internal server error" in {
        primeGatewayServiceWithBody(Status.INTERNAL_SERVER_ERROR)
        val box = createBoxWithSubscribersAndReturn()
        val result = doPost(s"$url/box/${box.boxId.raw}/notifications", "<somNode/>", validHeadersXml)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }


      "respond with 400 when boxId is not a UUID" in {
        val result = doPost(s"$url/box/ImNotaUUid/notifications", "{}", validHeadersJson)
        result.status shouldBe BAD_REQUEST
        result.body shouldBe "{\"code\":\"BAD_REQUEST\",\"message\":\"Box ID is not a UUID\"}"
      }

      "respond with 403 when no useragent sent in request" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.raw}/notifications", "{}", List("ContentType" -> "text/plain"))
        result.status shouldBe FORBIDDEN
      }

      "respond with 403 when non whitelisted user agent sent in request" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.raw}/notifications", "{}", List("ContentType" -> "text/plain", "User-Agent" -> "non-whitelisted-agent"))
        result.status shouldBe FORBIDDEN
      }


      "respond with 400 when for valid xml and but json content type" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.raw}/notifications", "<somNode/>", validHeadersJson)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 400 when for valid json and but xml content type" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.raw}/notifications", "{}", validHeadersXml)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 415 when unknown content type sent in request" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.raw}/notifications", "{}", List("ContentType" -> "text/plain", "User-Agent" -> "api-subscription-fields"))
        result.status shouldBe UNSUPPORTED_MEDIA_TYPE
      }

      "respond with 404 when unknown / non existent box id sent" in {
        createBoxAndReturn()
        val result = doPost(s"$url/box/${UUID.randomUUID().toString}/notifications/", "{}", validHeadersJson)
        result.status shouldBe NOT_FOUND
        result.body shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"
      }
    }

    "GET /box/[boxId]/notifications" should {
      "respond with 200 when notification exist and client is authorised" in {
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        createNotifications(box.boxId, 4)
        val result: WSResponse = doGet(s"$url/box/${box.boxId.raw}/notifications?status=PENDING", validHeadersJson)
        result.status shouldBe OK
      }

      "respond with 400 when box Id is not a UUID" in {
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        createNotifications(box.boxId, 4)
        val result: WSResponse = doGet(s"$url/box/NotAUUid/notifications?status=PENDING", validHeadersJson)
        result.status shouldBe BAD_REQUEST
        result.body shouldBe "{\"code\":\"BAD_REQUEST\",\"message\":\"Box ID is not a UUID\"}"
      }

      "respond with 404 when box Id is not found" in {
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        createNotifications(box.boxId, 1)
        val result: WSResponse = doGet(s"$url/box/${UUID.randomUUID().toString}/notifications?status=PENDING", validHeadersJson)
        result.status shouldBe NOT_FOUND
        result.body shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"
      }

      "respond with 401 on create when clientId returned from auth does not match" in {
        primeAuthServiceSuccess("UnknownClientId", "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        createNotifications(box.boxId, 4)
        val result: WSResponse = doGet(s"$url/box/${box.boxId.raw}/notifications?status=PENDING", validHeadersJson)
        result.status shouldBe FORBIDDEN
      }

      "respond with 401 on create when no clientID in response from auth" in {
        primeAuthServiceNoClientId("{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        createNotifications(box.boxId, 4)
        val result: WSResponse = doGet(s"$url/box/${box.boxId.raw}/notifications", validHeadersJson)
        result.status shouldBe UNAUTHORIZED
      }

      "respond with 401 when authorisation fails" in {
        primeAuthServiceFail()
        val box = createBoxAndReturn()
        createNotifications(box.boxId, 4)
        val result: WSResponse = doGet(s"$url/box/${box.boxId.raw}/notifications", validHeadersJson)
        result.status shouldBe UNAUTHORIZED
      }
    }

    "GET /box/[boxId]/notifications" should {

      "return 204 happy path" in {
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        val notifications = createNotifications(box.boxId, 4)
        val notificationIdList: List[String] = notifications.map(stringToCreateNotificationResponse(_)).map(_.notificationId)

        val result: WSResponse = doPut(s"$url/box/${box.boxId.raw}/notifications/acknowledge", buildAcknowledgeRequest(notificationIdList), validHeadersJson)
        result.status shouldBe NO_CONTENT
      }

      "return 204 when unknown UUID is included" in {
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        val notifications = createNotifications(box.boxId, 4)
        val notificationIdList: List[String] = UUID.randomUUID().toString :: notifications.map(stringToCreateNotificationResponse(_)).map(_.notificationId)

        val result: WSResponse = doPut(s"$url/box/${box.boxId.raw}/notifications/acknowledge", buildAcknowledgeRequest(notificationIdList), validHeadersJson)
        result.status shouldBe NO_CONTENT
      }

      "return 400 when invalid UUID is included" in {
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        val notifications = createNotifications(box.boxId, 4)
        val notificationIdList: List[String] = "fooBar" :: UUID.randomUUID().toString :: notifications.map(stringToCreateNotificationResponse(_)).map(_.notificationId)

        val result: WSResponse = doPut(s"$url/box/${box.boxId.raw}/notifications/acknowledge", buildAcknowledgeRequest(notificationIdList), validHeadersJson)
        result.status shouldBe BAD_REQUEST
      }

      "return 401 when no client id in auth response" in {
        primeAuthServiceNoClientId("{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        val notifications = createNotifications(box.boxId, 4)
        val notificationIdList: List[String] = notifications.map(stringToCreateNotificationResponse(_)).map(_.notificationId)

        val result: WSResponse = doPut(s"$url/box/${box.boxId.raw}/notifications/acknowledge", buildAcknowledgeRequest(notificationIdList), validHeadersJson)
        result.status shouldBe UNAUTHORIZED
        result.body shouldBe "{\"code\":\"UNAUTHORISED\",\"message\":\"Unable to retrieve ClientId\"}"
      }

      "return NOT_FOUND when box doesn't exist" in {
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        val notifications = createNotifications(box.boxId, 4)
        val notificationIdList: List[String] = notifications.map(stringToCreateNotificationResponse(_)).map(_.notificationId)

        val result: WSResponse = doPut(s"$url/box/${UUID.randomUUID().toString}/notifications/acknowledge", buildAcknowledgeRequest(notificationIdList), validHeadersJson)
        result.status shouldBe NOT_FOUND
      }
    }

  }

  private def buildAcknowledgeRequest(notificationIdList: List[String]): String = {
    Json.toJson(AcknowledgeNotificationsRequest(notificationIdList)).toString()
  }

  private def stringToCreateNotificationResponse(notificationIdResponse: String): CreateNotificationResponse = {
    Json.parse(notificationIdResponse).validate[CreateNotificationResponse] match {
      case JsSuccess(value: CreateNotificationResponse, _) => value
      case _ => fail()
    }
  }
}

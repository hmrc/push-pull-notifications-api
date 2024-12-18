/*
 * Copyright 2024 HM Revenue & Customs
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

import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import scala.collection.mutable

import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider

import play.api.http.HeaderNames.{CONTENT_TYPE, USER_AGENT}
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Format, JsSuccess, Json, Reads, Writes}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers._
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationId
import uk.gov.hmrc.pushpullnotificationsapi.models.{AcknowledgeNotificationsRequest, Box, BoxId, CreateNotificationResponse}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbNotification
import uk.gov.hmrc.pushpullnotificationsapi.repository.{BoxRepository, NotificationsRepository}
import uk.gov.hmrc.pushpullnotificationsapi.services.ChallengeGenerator
import uk.gov.hmrc.pushpullnotificationsapi.support._

class NotificationsControllerISpec
    extends ServerBaseISpec
    with BeforeAndAfterEach
    with PlayMongoRepositorySupport[DbNotification]
    with CleanMongoCollectionSupport
    with AuthService
    with AuditService
    with CallbackDestinationService
    with ThirdPartyApplicationService
    with ApiPlatformEventsService
    with ApplicationWithCollaboratorsFixtures
    with Eventually {

  this: Suite with ServerProvider =>

  val expectedChallenge = randomUUID.toString

  val stubbedChallengeGenerator: ChallengeGenerator = new ChallengeGenerator {
    override def generateChallenge: String = expectedChallenge
  }

  implicit val instantFormatter: Format[Instant] = Format(Reads.DefaultInstantReads, Writes.DefaultInstantWrites)
  def boxRepository: BoxRepository = app.injector.instanceOf[BoxRepository]

  def notificationRepo: NotificationsRepository = app.injector.instanceOf[NotificationsRepository]
  override protected val repository: PlayMongoRepository[DbNotification] = app.injector.instanceOf[NotificationsRepository]

  val boxName = "myboxName"
  val clientId = ClientId.random
  val createBoxJsonBody = raw"""{"clientId": "${clientId.value}", "boxName": "$boxName"}"""
  val createBox2JsonBody = raw"""{"clientId": "zzzzzzzzzz", "boxName": "bbyybybyb"}"""
  val tpaResponse: String = Json.toJson(standardApp.modify(_.copy(clientId = clientId))).toString()

  override def beforeEach(): Unit = {
    super.beforeEach()
    primeAuditService()
    primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
    await(boxRepository.ensureIndexes())
    await(notificationRepo.ensureIndexes())
  }

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "microservice.services.third-party-application.port" -> wireMockPort,
        "microservice.services.api-platform-events.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> true,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "validateHttpsCallbackUrl" -> false
      ).overrides(bind[ChallengeGenerator].to(stubbedChallengeGenerator))

  val url = s"http://localhost:$port"
  val callbackUrl = wireMockBaseUrlAsString + "/callback"

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

  def updateCallbackUrlRequestJson(boxClientId: ClientId): String =
    s"""
       |{
       | "clientId": "${boxClientId.value}",
       | "callbackUrl": "$callbackUrl"
       |}
       |""".stripMargin

  val acceptHeader: (String, String) = ACCEPT -> "application/vnd.hmrc.1.0+json"
  val validHeadersJson = List(acceptHeader, CONTENT_TYPE -> "application/json", USER_AGENT -> "api-subscription-fields", AUTHORIZATION -> "Bearer token")
  val validHeadersJsonWithNoUserAgent = List(acceptHeader, CONTENT_TYPE -> "application/json", AUTHORIZATION -> "Bearer token")
  val validHeadersXml = List(acceptHeader, CONTENT_TYPE -> "application/xml", USER_AGENT -> "api-subscription-fields", AUTHORIZATION -> "Bearer token")

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
      .get()
      .futureValue

  def callUpdateCallbackUrlEndpoint(boxId: BoxId, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box/${boxId.value.toString}/callback")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def createBoxAndReturn(): Box = {
    val result = doPut(s"$url/box", createBoxJsonBody, validHeadersJson)
    result.status shouldBe CREATED
    await(boxRepository.collection.find().toFuture()).head
  }

  def createNotifications(boxId: BoxId, numberToCreate: Int): List[String] = {
    val notifications: mutable.ListBuffer[String] = mutable.ListBuffer[String]()
    for (_ <- 0 until numberToCreate) {
      val result = doPost(s"$url/box/${boxId.value.toString}/notifications", "{}", validHeadersJson)
      result.status shouldBe CREATED
      notifications += result.body
    }
    List() ++ notifications
  }

  def createPushNotificationTestSetup(pushStatus: Int) = {
    primeCallBackUpdatedEndpoint(OK)
    primeDestinationServiceForCallbackValidation(Seq("challenge" -> expectedChallenge), OK, Some(Json.obj("challenge" -> expectedChallenge)))
    primeDestinationServiceForPushNotification(pushStatus)
  }

  "NotificationsController" when {

    "POST /box/[boxId]/notifications" should {
      "respond with 201 when notification created for valid json and json content type with push subscriber" in {
        createPushNotificationTestSetup(OK)
        val box = createBoxAndReturn()
        val validHeaders = List(CONTENT_TYPE -> "application/json", USER_AGENT -> "api-subscription-fields", AUTHORIZATION -> "Bearer token")
        callUpdateCallbackUrlEndpoint(box.boxId, updateCallbackUrlRequestJson(clientId), validHeaders)

        val result = doPost(s"$url/box/${box.boxId.value.toString}/notifications", """{"hello":"test"}""", validHeadersJson)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
        verifyCallback()
      }

      "respond with 201 when notification created for valid json and json content type with no subscriber" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.value.toString}/notifications", "{}", validHeadersJson)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 201 when notification created for valid xml and xml content type with no subscribers" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.value.toString}/notifications", "<somNode/>", validHeadersXml)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 201 when notification created for valid xml and xml content type even if push fails bad request" in {
        createPushNotificationTestSetup(BAD_REQUEST)
        val box = createBoxAndReturn()
        val validHeaders = List(CONTENT_TYPE -> "application/json", USER_AGENT -> "api-subscription-fields", AUTHORIZATION -> "Bearer token")
        callUpdateCallbackUrlEndpoint(box.boxId, updateCallbackUrlRequestJson(clientId), validHeaders)

        val result = doPost(s"$url/box/${box.boxId.value.toString}/notifications", "<somNode/>", validHeadersXml)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
        eventually {
          verifyCallback()
        }
      }

      "respond with 201 when notification created for valid xml and xml content type even if push fails internal server error" in {
        createPushNotificationTestSetup(INTERNAL_SERVER_ERROR)
        val box = createBoxAndReturn()
        val validHeaders = List(CONTENT_TYPE -> "application/json", USER_AGENT -> "api-subscription-fields", AUTHORIZATION -> "Bearer token")
        callUpdateCallbackUrlEndpoint(box.boxId, updateCallbackUrlRequestJson(clientId), validHeaders)
        val result = doPost(s"$url/box/${box.boxId.value.toString}/notifications", "<somNode/>", validHeadersXml)

        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
        eventually {
          verifyCallback()
        }
      }

      "respond with 400 when boxId is not a UUID" in {
        val result = doPost(s"$url/box/ImNotaUUid/notifications", "{}", validHeadersJson)
        result.status shouldBe BAD_REQUEST
        result.body shouldBe "{\"code\":\"BAD_REQUEST\",\"message\":\"Box ID is not a UUID\"}"
      }

      "respond with 403 when no useragent sent in request" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.value.toString}/notifications", "{}", List("ContentType" -> "text/plain"))
        result.status shouldBe FORBIDDEN
      }

      "respond with 403 when non allowlisted user agent sent in request" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.value.toString}/notifications", "{}", List("ContentType" -> "text/plain", "User-Agent" -> "non-allowlisted-agent"))
        result.status shouldBe FORBIDDEN
      }

      "respond with 400 when for valid xml and but json content type" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.value.toString}/notifications", "<somNode/>", validHeadersJson)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 400 when for valid json and but xml content type" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.value.toString}/notifications", "{}", validHeadersXml)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 415 when unknown content type sent in request" in {
        val box = createBoxAndReturn()
        val result = doPost(s"$url/box/${box.boxId.value.toString}/notifications", "{}", List("ContentType" -> "text/plain", "User-Agent" -> "api-subscription-fields"))
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
        val result: WSResponse = doGet(s"$url/box/${box.boxId.value.toString}/notifications?status=PENDING", validHeadersJson)
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
        val unknownClientId = ClientId.random
        primeAuthServiceSuccess(unknownClientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        createNotifications(box.boxId, 4)
        val result: WSResponse = doGet(s"$url/box/${box.boxId.value.toString}/notifications?status=PENDING", validHeadersJson)
        result.status shouldBe FORBIDDEN
      }

      "respond with 401 on create when no clientID in response from auth" in {
        primeAuthServiceNoClientId("{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        createNotifications(box.boxId, 4)
        val result: WSResponse = doGet(s"$url/box/${box.boxId.value.toString}/notifications", validHeadersJson)
        result.status shouldBe UNAUTHORIZED
      }

      "respond with 401 when authorisation fails" in {
        primeAuthServiceFail()
        val box = createBoxAndReturn()
        createNotifications(box.boxId, 4)
        val result: WSResponse = doGet(s"$url/box/${box.boxId.value.toString}/notifications", validHeadersJson)
        result.status shouldBe UNAUTHORIZED
      }
    }

    "GET /box/[boxId]/notifications" should {

      "return 204 happy path" in {
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        val notifications = createNotifications(box.boxId, 4)
        val notificationIdList: List[NotificationId] = notifications.map(stringToCreateNotificationResponse).map(_.notificationId)

        val result: WSResponse = doPut(s"$url/box/${box.boxId.value.toString}/notifications/acknowledge", buildAcknowledgeRequest(notificationIdList), validHeadersJson)
        result.status shouldBe NO_CONTENT
      }

      "return 204 when unknown UUID is included" in {
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        val notifications = createNotifications(box.boxId, 4)
        val notificationIdList: List[NotificationId] = NotificationId.random :: notifications.map(stringToCreateNotificationResponse).map(_.notificationId)

        val result: WSResponse = doPut(s"$url/box/${box.boxId.value.toString}/notifications/acknowledge", buildAcknowledgeRequest(notificationIdList), validHeadersJson)
        result.status shouldBe NO_CONTENT
      }

      "return 401 when no client id in auth response" in {
        primeAuthServiceNoClientId("{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        val notifications = createNotifications(box.boxId, 4)
        val notificationIdList: List[NotificationId] = notifications.map(stringToCreateNotificationResponse).map(_.notificationId)

        val result: WSResponse = doPut(s"$url/box/${box.boxId.value.toString}/notifications/acknowledge", buildAcknowledgeRequest(notificationIdList), validHeadersJson)
        result.status shouldBe UNAUTHORIZED
        result.body shouldBe "{\"code\":\"UNAUTHORISED\",\"message\":\"Unable to retrieve ClientId\"}"
      }

      "return NOT_FOUND when box doesn't exist" in {
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val box = createBoxAndReturn()
        val notifications = createNotifications(box.boxId, 4)
        val notificationIdList: List[NotificationId] = notifications.map(stringToCreateNotificationResponse).map(_.notificationId)

        val result: WSResponse = doPut(s"$url/box/${UUID.randomUUID().toString}/notifications/acknowledge", buildAcknowledgeRequest(notificationIdList), validHeadersJson)
        result.status shouldBe NOT_FOUND
      }
    }

  }

  private def buildAcknowledgeRequest(notificationIdList: List[NotificationId]): String = {
    Json.toJson(AcknowledgeNotificationsRequest(notificationIdList)).toString()
  }

  private def stringToCreateNotificationResponse(notificationIdResponse: String): CreateNotificationResponse = {
    Json.parse(notificationIdResponse).validate[CreateNotificationResponse] match {
      case JsSuccess(value: CreateNotificationResponse, _) => value
      case _                                               => fail()
    }
  }

}

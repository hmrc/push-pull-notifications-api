package uk.gov.hmrc.pushpullnotificationsapi.controllers

import java.util.UUID
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.http.HeaderNames.{ACCEPT, CONTENT_TYPE, USER_AGENT}
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{BAD_REQUEST, CREATED, FORBIDDEN, NOT_FOUND, OK, UNAUTHORIZED, UNSUPPORTED_MEDIA_TYPE}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, PullSubscriber, PushSubscriber, UpdateCallbackUrlResponse}
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.BoxFormat.boxFormats
import uk.gov.hmrc.pushpullnotificationsapi.support.{AuthService, MongoApp, PushGatewayService, ServerBaseISpec, ThirdPartyApplicationService}

import scala.concurrent.ExecutionContext.Implicits.global

class BoxControllerISpec extends ServerBaseISpec
  with AuthService
  with BeforeAndAfterEach
  with PlayMongoRepositorySupport[Box]
  with CleanMongoCollectionSupport
  with PushGatewayService
  with ThirdPartyApplicationService {
  this: Suite with ServerProvider =>

  def repo: BoxRepository =
    app.injector.instanceOf[BoxRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  override protected def repository: PlayMongoRepository[Box] = new BoxRepository(mongoComponent)

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "microservice.services.push-pull-notifications-gateway.port" -> wireMockPort,
        "microservice.services.push-pull-notifications-gateway.authorizationKey" -> "iampushpullapi",
        "microservice.services.third-party-application.port" -> wireMockPort
      )

  val url = s"http://localhost:$port"


  val boxName = "myBoxName"
  val clientId = "someClientId"
  val clientId2 = "someClientId2"
  val createClientManagedBoxJsonBody = raw"""{"boxName": "$boxName"}"""
  val createClientManagedBox2JsonBody = raw"""{"boxName": "bbyybybyb"}"""
  val createBoxJsonBody = raw"""{"clientId": "$clientId", "boxName": "$boxName"}"""
  val createBox2JsonBody = raw"""{"clientId":  "$clientId2", "boxName": "bbyybybyb"}"""
  val tpaResponse: String = raw"""{"id":  "someappid", "clientId": "$clientId"}"""
  val updateSubscriberJsonBodyWithIds: String =
    raw"""{ "subscriber":
         |  {
         |     "subscriberType": "API_PUSH_SUBSCRIBER",
         |     "callBackUrl":"somepath/firstOne"
         |  }
         |}
         |""".stripMargin

  val validHeaders = List(CONTENT_TYPE -> "application/json", USER_AGENT -> "api-subscription-fields")
  val validHeadersWithAcceptHeader = List(CONTENT_TYPE -> "application/json", ACCEPT -> "application/vnd.hmrc.1.0+json")

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def callCreateBoxEndpoint(jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue
  def callClientManagedCreateBoxEndpoint(jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/cmb/box")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def callUpdateSubscriberEndpoint(boxId: String, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box/$boxId/subscriber")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def callUpdateCallbackUrlEndpoint(boxId: String, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box/$boxId/callback")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def callGetBoxByNameAndClientIdEndpoint(boxName: String, clientId: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box?boxName=$boxName&clientId=$clientId")
      .withHttpHeaders(headers: _*)
      .get
      .futureValue

  def callGetBoxesByClientIdEndpoint(headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/cmb/box")
      .withHttpHeaders(headers: _*)
      .get
      .futureValue
  // need to clean down mongo then run two


  "BoxController" when {

    "GET /cmb/box" should {
      val additionalHeader = (ACCEPT -> "application/vnd.hmrc.1.0+json")
      "respond with empty list" in {
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")

        val result = callGetBoxesByClientIdEndpoint(validHeaders :+ additionalHeader)

        result.status shouldBe OK
        result.body shouldBe "[]"
      }

      "respond with box when one created" in {
        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")

        callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
        val result = callGetBoxesByClientIdEndpoint(validHeaders :+ additionalHeader)
  
        result.status shouldBe OK
        result.body should include(s""""boxName":"DEFAULT"""")
      }

      "respond with client managed box when one created" in {
        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")

        callClientManagedCreateBoxEndpoint(createClientManagedBoxJsonBody, validHeadersWithAcceptHeader)
        val result = callGetBoxesByClientIdEndpoint(validHeadersWithAcceptHeader)

        result.status shouldBe OK
        result.body should include(s""""boxName":"$boxName"""")
      }
    }

    "POST /box" should {
      "respond with 201 when box created" in {

        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        val result = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 200 with box ID  when box already exists" in {
        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        val result1 = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
        validateStringIsUUID(result1.body)

        val result2 = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
        result2.status shouldBe OK
        validateStringIsUUID(result2.body)
        result2.body shouldBe result1.body
      }

      "respond with 201 when two boxs are created" in {
        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        val result = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)

        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId2)
        val result2 = callCreateBoxEndpoint(createBox2JsonBody, validHeaders)
        result2.status shouldBe CREATED
        validateStringIsUUID(result2.body)
      }

      "respond with 400 when NonJson is sent" in {
        val result = callCreateBoxEndpoint("nonJsonPayload", validHeaders)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 400 when invalid Json is sent" in {
        val result = callCreateBoxEndpoint("{}", validHeaders)
        result.status shouldBe BAD_REQUEST
        result.body.contains("INVALID_REQUEST_PAYLOAD") shouldBe true
      }

      "respond with 415 when request content Type headers are empty" in {
        val result = callCreateBoxEndpoint("{}", List("someHeader" -> "someValue"))
        result.status shouldBe UNSUPPORTED_MEDIA_TYPE
      }

      "respond with 415 when request content Type header is not JSON " in {
        val result = callCreateBoxEndpoint("{}", List("Content-Type" -> "application/xml"))
        result.status shouldBe UNSUPPORTED_MEDIA_TYPE
      }

      "respond with 403 when UserAgent is not sent " in {
        val result = callCreateBoxEndpoint("{}", List("Content-Type" -> "application/json"))
        result.status shouldBe FORBIDDEN
      }

      "respond with 403 when UserAgent is not in allowlist" in {
        val result = callCreateBoxEndpoint("{}", List("Content-Type" -> "application/json", "User-Agent" -> "not-a-known-one"))
        result.status shouldBe FORBIDDEN
      }

      "respond with 404 when invalid uri provided" in {
        val result = wsClient
          .url(s"$url/box/unKnownPath")
          .withHttpHeaders(validHeaders: _*)
          .get
          .futureValue

        result.status shouldBe NOT_FOUND
        result.body shouldBe "{\"code\":\"NOT_FOUND\",\"message\":\"URI not found /box/unKnownPath\"}"
      }
    }
    "PUT /cmb/box" should {
      "respond with 201 when box created" in {
        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val result = callClientManagedCreateBoxEndpoint(createClientManagedBoxJsonBody, validHeadersWithAcceptHeader)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 200 with box ID  when box already exists" in {
        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val result1 = callClientManagedCreateBoxEndpoint(createClientManagedBoxJsonBody, validHeadersWithAcceptHeader)
        validateStringIsUUID(result1.body)

        val result2 = callClientManagedCreateBoxEndpoint(createClientManagedBoxJsonBody, validHeadersWithAcceptHeader)
        result2.status shouldBe OK
        validateStringIsUUID(result2.body)
        result2.body shouldBe result1.body
      }

      "respond with 201 when two boxs are created" in {
        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val result = callClientManagedCreateBoxEndpoint(createClientManagedBoxJsonBody, validHeadersWithAcceptHeader)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)

        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId2)
        val result2 = callClientManagedCreateBoxEndpoint(createClientManagedBox2JsonBody, validHeadersWithAcceptHeader)
        result2.status shouldBe CREATED
        validateStringIsUUID(result2.body)
      }

      "respond with 400 when NonJson is sent" in {
        val result = callClientManagedCreateBoxEndpoint("nonJsonPayload", validHeadersWithAcceptHeader)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 400 when invalid Json is sent" in {
        primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
        val result = callClientManagedCreateBoxEndpoint("{}", validHeadersWithAcceptHeader)
        result.status shouldBe BAD_REQUEST
        result.body.contains("INVALID_REQUEST_PAYLOAD") shouldBe true
      }

      "respond with 415 when request content Type headers are empty" in {
        val result = callClientManagedCreateBoxEndpoint("{}", List("someHeader" -> "someValue"))
        result.status shouldBe UNSUPPORTED_MEDIA_TYPE
      }

      "respond with 415 when request content Type header is not JSON " in {
        val result = callClientManagedCreateBoxEndpoint("{}", List("Content-Type" -> "application/xml"))
        result.status shouldBe UNSUPPORTED_MEDIA_TYPE
      }

      "respond with 404 when invalid uri provided" in {
        val result = wsClient
          .url(s"$url/box/unKnownPath")
          .withHttpHeaders(validHeaders: _*)
          .get
          .futureValue

        result.status shouldBe NOT_FOUND
        result.body shouldBe "{\"code\":\"NOT_FOUND\",\"message\":\"URI not found /box/unKnownPath\"}"
      }
    }
  }

  "GET /box?boxName=someName&clientId=someClientid" should {
    "respond with 200 and box in body when exists" in {

      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      val result = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
      result.status shouldBe CREATED
      validateStringIsUUID(result.body)

      val result2 = callGetBoxByNameAndClientIdEndpoint(boxName, clientId, validHeaders)
      result2.status shouldBe OK

      val box = Json.parse(result2.body).as[Box]
      box.boxName shouldBe boxName
      box.boxCreator.clientId.value shouldBe clientId

    }

    "respond with 404 when empty client id provided" in {

      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      val result = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
      result.status shouldBe CREATED
      validateStringIsUUID(result.body)

      val result2 = callGetBoxByNameAndClientIdEndpoint(boxName, "", validHeaders)
      result2.status shouldBe NOT_FOUND
      result2.body shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"

    }

    "respond with 404 when box does not exists" in {
      val result = callGetBoxByNameAndClientIdEndpoint(boxName, clientId, validHeaders)
      result.status shouldBe NOT_FOUND
      result.body shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"
    }
  }

  "PUT /box/{boxId}/callback" should {
    val callbackUrl = "https://some.callback.url"

    def updateCallbackUrlRequestJson(boxClientId: String): String =
      s"""
         |{
         | "clientId": "$boxClientId",
         | "callbackUrl": "$callbackUrl"
         |}
         |""".stripMargin

    def updateCallbackUrlRequestJsonNoCallBack(boxClientId: String): String =
      s"""
         |{
         | "clientId": "$boxClientId",
         | "callbackUrl": ""
         |}
         |""".stripMargin

    "return 200 with {successful:true} and update box successfully when Callback Url is validated" in {
      primeGatewayServiceValidateCallBack(OK)

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId.raw, updateCallbackUrlRequestJson(clientId), validHeaders)
      updateResult.status shouldBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful shouldBe true

      val updatedBox = await(repo.findByBoxId(createdBox.boxId))
      updatedBox.get.subscriber.get.asInstanceOf[PushSubscriber].callBackUrl shouldBe callbackUrl
    }

    "return 200 with {successful:true} and update box successfully when Callback Url is empty" in {

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId.raw, updateCallbackUrlRequestJsonNoCallBack(clientId), validHeaders)
      updateResult.status shouldBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful shouldBe true

      val updatedBox = await(repo.findByBoxId(createdBox.boxId))
      updatedBox.get.subscriber.get.asInstanceOf[PullSubscriber].callBackUrl.isEmpty shouldBe true
    }

    "return 401 when useragent header is missing" in {
      primeGatewayServiceValidateCallBack(OK)

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId.raw, updateCallbackUrlRequestJson(clientId),  List("Content-Type" -> "application/json"))
      updateResult.status shouldBe FORBIDDEN

    }

    "return 200 with {successful:false} when Callback Url cannot be validated" in {
      val errorMessage = "Unable to verify callback url"
      primeGatewayServiceValidateCallBack(OK, successfulResult = false, Some(errorMessage))

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId.raw, updateCallbackUrlRequestJson(clientId), validHeaders)
      updateResult.status shouldBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful shouldBe false
      responseBody.errorMessage shouldBe Some(errorMessage)
    }

    "return 401 when clientId does not match that on the Box" in {
      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId.raw, updateCallbackUrlRequestJson("notTheCorrectClientId"), validHeaders)

      updateResult.status shouldBe UNAUTHORIZED
    }

    "return 404 when box does not exist" in {
      val updateResult = callUpdateCallbackUrlEndpoint(UUID.randomUUID().toString, updateCallbackUrlRequestJson(clientId), validHeaders)
      updateResult.status shouldBe NOT_FOUND
      updateResult.body shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"
    }

    "return 400 when boxId is not a UUID" in {
      val updateResult = callUpdateCallbackUrlEndpoint("NotaUUid", updateCallbackUrlRequestJson(clientId), validHeaders)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"BAD_REQUEST\",\"message\":\"Box ID is not a UUID\"}"
    }

    "return 400 when requestBody is not a valid payload" in {
      val updateResult = callUpdateCallbackUrlEndpoint(UUID.randomUUID().toString, "{}", validHeaders)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }

    "return 400 when requestBody is not a valid payload against expected format" in {
      val updateResult = callUpdateCallbackUrlEndpoint(UUID.randomUUID().toString, "{\"foo\":\"bar\"}", validHeaders)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }

    "return 400 when requestBody is missing" in {
      val updateResult = callUpdateCallbackUrlEndpoint(UUID.randomUUID().toString, "", validHeaders)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }
  }

  private def createBoxAndCheckExistsWithNoSubscribers(): Box = {

    primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)

    val result = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
    result.status shouldBe CREATED
    validateStringIsUUID(result.body)

    val result2 = callGetBoxByNameAndClientIdEndpoint(boxName, clientId, validHeaders)
    result2.status shouldBe OK
    val box = Json.parse(result2.body).as[Box]
    box.subscriber.isDefined shouldBe false
    box
  }

}

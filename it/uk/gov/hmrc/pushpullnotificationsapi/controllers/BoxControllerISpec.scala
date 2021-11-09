package uk.gov.hmrc.pushpullnotificationsapi.controllers

import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatestplus.play.ServerProvider
import play.api.http.HeaderNames.{CONTENT_TYPE, USER_AGENT}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{BAD_REQUEST, CREATED, FORBIDDEN, NOT_FOUND, OK, UNAUTHORIZED, UNSUPPORTED_MEDIA_TYPE}
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, PullSubscriber, PushSubscriber, UpdateCallbackUrlResponse}
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository
import uk.gov.hmrc.pushpullnotificationsapi.support.{MongoApp, NewBaseISpec, PushGatewayService, ThirdPartyApplicationService}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class BoxControllerISpec extends NewBaseISpec  with MongoApp with PushGatewayService with ThirdPartyApplicationService {
  this: Suite with ServerProvider =>


  trait Setup {
     val repo: BoxRepository =
      app.injector.instanceOf[BoxRepository]
    await(repo.ensureIndexes)
    dropMongoDb()
  }


  val url = s"http://localhost:6001"


  val boxName = "myBoxName"
  val clientId = "someClientId"
  val clientId2 = "someClientId2"
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

  private lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def callCreateBoxEndpoint(jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box")
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

  // need to clean down mongo then run two


  "BoxController" when {

    "POST /box" should {
      "respond with 201 when box created" in {

        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        val result = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
        result.status mustBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 200 with box ID  when box already exists" in {
        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        val result1 = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
        validateStringIsUUID(result1.body)

        val result2 = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
        result2.status mustBe OK
        validateStringIsUUID(result2.body)
        result2.body mustBe result1.body
      }

      "respond with 201 when two boxs are created" in {
        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        val result = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
        result.status mustBe CREATED
        validateStringIsUUID(result.body)

        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId2)
        val result2 = callCreateBoxEndpoint(createBox2JsonBody, validHeaders)
        result2.status mustBe CREATED
        validateStringIsUUID(result2.body)
      }

      "respond with 400 when NonJson is sent" in {
        val result = callCreateBoxEndpoint("nonJsonPayload", validHeaders)
        result.status mustBe BAD_REQUEST
      }

      "respond with 400 when invalid Json is sent" in {
        val result = callCreateBoxEndpoint("{}", validHeaders)
        result.status mustBe BAD_REQUEST
        result.body.contains("INVALID_REQUEST_PAYLOAD") mustBe true
      }

      "respond with 415 when request content Type headers are empty" in {
        val result = callCreateBoxEndpoint("{}", List("someHeader" -> "someValue"))
        result.status mustBe UNSUPPORTED_MEDIA_TYPE
      }

      "respond with 415 when request content Type header is not JSON " in {
        val result = callCreateBoxEndpoint("{}", List("Content-Type" -> "application/xml"))
        result.status mustBe UNSUPPORTED_MEDIA_TYPE
      }

      "respond with 403 when UserAgent is not sent " in {
        val result = callCreateBoxEndpoint("{}", List("Content-Type" -> "application/json"))
        result.status mustBe FORBIDDEN
      }

      "respond with 403 when UserAgent is not in whitelist" in {
        val result = callCreateBoxEndpoint("{}", List("Content-Type" -> "application/json", "User-Agent" -> "not-a-known-one"))
        result.status mustBe FORBIDDEN
      }

      "respond with 404 when invalid uri provided" in {
        val result = wsClient
          .url(s"$url/box/unKnownPath")
          .withHttpHeaders(validHeaders: _*)
          .get
          .futureValue

        result.status mustBe NOT_FOUND
        result.body mustBe "{\"code\":\"NOT_FOUND\",\"message\":\"URI not found /box/unKnownPath\"}"
      }
    }
  }

  "GET /box?boxName=someName&clientId=someClientid" should {
    "respond with 200 and box in body when exists" in {

      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      val result = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
      result.status mustBe CREATED
      validateStringIsUUID(result.body)

      val result2 = callGetBoxByNameAndClientIdEndpoint(boxName, clientId, validHeaders)
      result2.status mustBe OK

      val box = Json.parse(result2.body).as[Box]
      box.boxName mustBe boxName
      box.boxCreator.clientId.value mustBe clientId

    }

    "respond with 404 when box does not exists" in {
      val result = callGetBoxByNameAndClientIdEndpoint(boxName, clientId, validHeaders)
      result.status mustBe NOT_FOUND
      result.body mustBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"
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

    "return 200 with {successful:true} and update box successfully when Callback Url is validated" in new Setup{
      primeGatewayServiceValidateCallBack(OK)

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId.raw, updateCallbackUrlRequestJson(clientId), validHeaders)
      updateResult.status mustBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful mustBe true

      val updatedBox = await(repo.findByBoxId(createdBox.boxId))
      updatedBox.get.subscriber.get.asInstanceOf[PushSubscriber].callBackUrl mustBe callbackUrl
    }

    "return 200 with {successful:true} and update box successfully when Callback Url is empty" in new Setup {

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId.raw, updateCallbackUrlRequestJsonNoCallBack(clientId), validHeaders)
      updateResult.status mustBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful mustBe true

      val updatedBox = await(repo.findByBoxId(createdBox.boxId))
      updatedBox.get.subscriber.get.asInstanceOf[PullSubscriber].callBackUrl.isEmpty mustBe true
    }

    "return 401 when useragent header is missing" in {
      primeGatewayServiceValidateCallBack(OK)

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId.raw, updateCallbackUrlRequestJson(clientId),  List("Content-Type" -> "application/json"))
      updateResult.status mustBe FORBIDDEN

    }

    "return 200 with {successful:false} when Callback Url cannot be validated" in {
      val errorMessage = "Unable to verify callback url"
      primeGatewayServiceValidateCallBack(OK, successfulResult = false, Some(errorMessage))

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId.raw, updateCallbackUrlRequestJson(clientId), validHeaders)
      updateResult.status mustBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful mustBe false
      responseBody.errorMessage mustBe Some(errorMessage)
    }

    "return 401 when clientId does not match that on the Box" in {
      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId.raw, updateCallbackUrlRequestJson("notTheCorrectClientId"), validHeaders)

      updateResult.status mustBe UNAUTHORIZED
    }

    "return 404 when box does not exist" in {
      val updateResult = callUpdateCallbackUrlEndpoint(UUID.randomUUID().toString, updateCallbackUrlRequestJson(clientId), validHeaders)
      updateResult.status mustBe NOT_FOUND
      updateResult.body mustBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"
    }

    "return 400 when boxId is not a UUID" in {
      val updateResult = callUpdateCallbackUrlEndpoint("NotaUUid", updateCallbackUrlRequestJson(clientId), validHeaders)
      updateResult.status mustBe BAD_REQUEST
      updateResult.body mustBe "{\"code\":\"BAD_REQUEST\",\"message\":\"Box ID is not a UUID\"}"
    }

    "return 400 when requestBody is not a valid payload" in {
      val updateResult = callUpdateCallbackUrlEndpoint(UUID.randomUUID().toString, "{}", validHeaders)
      updateResult.status mustBe BAD_REQUEST
      updateResult.body mustBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }

    "return 400 when requestBody is not a valid payload against expected format" in {
      val updateResult = callUpdateCallbackUrlEndpoint(UUID.randomUUID().toString, "{\"foo\":\"bar\"}", validHeaders)
      updateResult.status mustBe BAD_REQUEST
      updateResult.body mustBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }

    "return 400 when requestBody is missing" in {
      val updateResult = callUpdateCallbackUrlEndpoint(UUID.randomUUID().toString, "", validHeaders)
      updateResult.status mustBe BAD_REQUEST
      updateResult.body mustBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }
  }

  private def createBoxAndCheckExistsWithNoSubscribers(): Box = {

    primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)

    val result = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
    result.status mustBe CREATED
    validateStringIsUUID(result.body)

    val result2 = callGetBoxByNameAndClientIdEndpoint(boxName, clientId, validHeaders)
    result2.status mustBe OK
    val box = Json.parse(result2.body).as[Box]
    box.subscriber.isDefined mustBe false
    box
  }

}

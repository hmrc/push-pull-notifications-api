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

import java.util.UUID.randomUUID

import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider

import play.api.http.HeaderNames.{ACCEPT, CONTENT_TYPE, USER_AGENT}
import play.api.http.Status
import play.api.http.Status.NO_CONTENT
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers._
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.BoxFormat.boxFormats
import uk.gov.hmrc.pushpullnotificationsapi.services.ChallengeGenerator
import uk.gov.hmrc.pushpullnotificationsapi.support.{AuthService, PushGatewayService, ServerBaseISpec, ThirdPartyApplicationService}

class BoxControllerISpec
    extends ServerBaseISpec
    with AuthService
    with BeforeAndAfterEach
    with PlayMongoRepositorySupport[Box]
    with CleanMongoCollectionSupport
    with IntegrationPatience
    with PushGatewayService
    with ThirdPartyApplicationService {
  this: Suite with ServerProvider =>

  def repo: BoxRepository =
    app.injector.instanceOf[BoxRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repo.ensureIndexes())
  }

  val stubbedChallengeGenerator: ChallengeGenerator = new ChallengeGenerator {
    override def generateChallenge: String = expectedChallenge
  }

  override protected val repository: PlayMongoRepository[Box] = app.injector.instanceOf[BoxRepository]

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
        "microservice.services.third-party-application.port" -> wireMockPort,
        "validateHttpsCallbackUrl" -> false
      ).overrides(bind[ChallengeGenerator].to(stubbedChallengeGenerator))

  val url = s"http://localhost:$port"

  val boxName = "myBoxName"
  val clientId = ClientId.random
  val clientId2 = ClientId.random
  val createClientManagedBoxJsonBody = raw"""{"boxName": "$boxName"}"""
  val createClientManagedBox2JsonBody = raw"""{"boxName": "bbyybybyb"}"""
  val createBoxJsonBody = raw"""{"clientId": "${clientId.value}", "boxName": "$boxName"}"""
  val createBox2JsonBody = raw"""{"clientId":  "${clientId2.value}", "boxName": "bbyybybyb"}"""
  val expectedChallenge = randomUUID.toString
  val tpaResponse: String = raw"""{"id":  "931cbba3-c2ae-4078-af8a-b7fbcb804758", "clientId": "${clientId.value}"}"""

  val updateSubscriberJsonBodyWithIds: String =
    raw"""{ "subscriber":
         |  {
         |     "subscriberType": "API_PUSH_SUBSCRIBER",
         |     "callBackUrl":"somepath/firstOne"
         |  }
         |}
         |""".stripMargin

  val validHeaders = List(CONTENT_TYPE -> "application/json", USER_AGENT -> "api-subscription-fields", AUTHORIZATION -> "Bearer token")

  val validHeadersWithAcceptHeader =
    List(CONTENT_TYPE -> "application/json", USER_AGENT -> "api-subscription-fields", ACCEPT -> "application/vnd.hmrc.1.0+json", AUTHORIZATION -> "Bearer token")
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

  def callUpdateSubscriberEndpoint(boxId: BoxId, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box/${boxId.value.toString}/subscriber")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def callUpdateCallbackUrlEndpoint(boxId: BoxId, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box/${boxId.value.toString}/callback")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def callClientManagedUpdateCallbackUrlEndpoint(boxId: BoxId, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/cmb/box/${boxId.value.toString}/callback")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def callGetBoxByNameAndClientIdEndpoint(boxName: String, clientId: ClientId, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box?boxName=$boxName&clientId=${clientId.value}")
      .withHttpHeaders(headers: _*)
      .get()
      .futureValue

  def callGetBoxByNameAndEmptyClientIdEndpoint(boxName: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box?boxName=$boxName&clientId=")
      .withHttpHeaders(headers: _*)
      .get()
      .futureValue

  def callGetBoxesByClientIdEndpoint(headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/cmb/box")
      .withHttpHeaders(headers: _*)
      .get()
      .futureValue

  def callValidateBoxEndpoint(jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/cmb/validate")
      .withHttpHeaders(headers: _*)
      .post(jsonBody)
      .futureValue

  // need to clean down mongo then run two

  def callDeleteClientManagedBoxEndpoint(boxId: BoxId, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/cmb/box/${boxId.value.toString}")
      .withHttpHeaders(headers: _*)
      .delete()
      .futureValue

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
          .get()
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
          .get()
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
      box.boxCreator.clientId shouldBe clientId

    }

    "respond with 404 when empty client id provided" in {

      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      val result = callCreateBoxEndpoint(createBoxJsonBody, validHeaders)
      result.status shouldBe CREATED
      validateStringIsUUID(result.body)

      val result2 = callGetBoxByNameAndEmptyClientIdEndpoint(boxName, validHeaders)
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
    val callbackUrl = wireMockBaseUrlAsString + "/callback"

    def updateCallbackUrlRequestJson(boxClientId: ClientId): String =
      s"""
         |{
         | "clientId": "${boxClientId.value}",
         | "callbackUrl": "$callbackUrl"
         |}
         |""".stripMargin

    def updateCallbackUrlRequestJsonNoCallBack(boxClientId: ClientId): String =
      s"""
         |{
         | "clientId": "${boxClientId.value}",
         | "callbackUrl": ""
         |}
         |""".stripMargin

    // --------------------------------------------------------------------------------------------------------

    "return 200 with {successful:true} and update box successfully when Callback Url is validated" in {
      primeDestinationServiceForValidation(Seq("challenge" -> expectedChallenge), OK, Some(Json.obj("challenge" -> expectedChallenge)))

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId, updateCallbackUrlRequestJson(clientId), validHeaders)
      updateResult.status shouldBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful shouldBe true

      val updatedBox = await(repo.findByBoxId(createdBox.boxId))
      updatedBox.get.subscriber.get.asInstanceOf[PushSubscriber].callBackUrl shouldBe callbackUrl
    }

    "return 200 with {successful:true} and update box successfully when Callback Url is empty" in {

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId, updateCallbackUrlRequestJsonNoCallBack(clientId), validHeaders)
      updateResult.status shouldBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful shouldBe true

      val updatedBox = await(repo.findByBoxId(createdBox.boxId))
      updatedBox.get.subscriber.get.asInstanceOf[PullSubscriber].callBackUrl.isEmpty shouldBe true
    }

    "return 401 when useragent header is missing" in {
      primeGatewayServiceValidateCallBack(OK)

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId, updateCallbackUrlRequestJson(clientId), List("Content-Type" -> "application/json"))
      updateResult.status shouldBe FORBIDDEN

    }

    "return 200 with {successful:false} when Callback Url cannot be validated" in {
      val errorMessage = "Unable to verify callback url"
      primeGatewayServiceValidateCallBack(OK, successfulResult = false, Some(errorMessage))

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId, updateCallbackUrlRequestJson(clientId), validHeaders)
      updateResult.status shouldBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful shouldBe false
      responseBody.errorMessage shouldBe Some(errorMessage)
    }

    "return 401 when clientId does not match that on the Box" in {
      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId, updateCallbackUrlRequestJson(ClientId.random), validHeaders)

      updateResult.status shouldBe UNAUTHORIZED
    }

    "return 404 when box does not exist" in {
      val updateResult = callUpdateCallbackUrlEndpoint(BoxId.random, updateCallbackUrlRequestJson(clientId), validHeaders)
      updateResult.status shouldBe NOT_FOUND
      updateResult.body shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"
    }
//TODO check JSON? cqant send invalid UUID
//    "return 400 when boxId is not a UUID" in {
//      val updateResult = callUpdateCallbackUrlEndpoint("NotaUUid", updateCallbackUrlRequestJson(clientId), validHeaders)
//      updateResult.status shouldBe BAD_REQUEST
//      updateResult.body shouldBe "{\"code\":\"BAD_REQUEST\",\"message\":\"Box ID is not a UUID\"}"
//    }

    "return 400 when requestBody is not a valid payload" in {
      val updateResult = callUpdateCallbackUrlEndpoint(BoxId.random, "{}", validHeaders)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }

    "return 400 when requestBody is not a valid payload against expected format" in {
      val updateResult = callUpdateCallbackUrlEndpoint(BoxId.random, "{\"foo\":\"bar\"}", validHeaders)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }

    "return 400 when requestBody is missing" in {
      val updateResult = callUpdateCallbackUrlEndpoint(BoxId.random, "", validHeaders)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }
  }

  "DELETE /cmb/box/$boxId" should {
    val boxId: BoxId = BoxId.random
    val clientId: ClientId = ClientId("someClientId")
    val clientManagedBox: Box = Box(boxName = "boxName", boxId = boxId, boxCreator = BoxCreator(clientId), clientManaged = true)

    "successfully delete a CMB and return status 204" in {
      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
      await(repo.createBox(clientManagedBox))
      val deleteResult = callDeleteClientManagedBoxEndpoint(clientManagedBox.boxId, validHeadersWithAcceptHeader)
      deleteResult.status shouldBe NO_CONTENT
    }

    "failed to delete a CMB when not found and return status 404" in {
      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
      await(repo.createBox(clientManagedBox))
      val deleteResult = callDeleteClientManagedBoxEndpoint(BoxId.random, validHeadersWithAcceptHeader)
      deleteResult.status shouldBe NOT_FOUND
    }

    "failed to delete a CMB when unauthorised and return status 403" in {
      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
      val notClientManaged = clientManagedBox.copy(clientManaged = false)
      await(repo.createBox(notClientManaged))
      val deleteResult = callDeleteClientManagedBoxEndpoint(notClientManaged.boxId, validHeadersWithAcceptHeader)
      deleteResult.status shouldBe FORBIDDEN
    }

    "failed to delete a CMB when caller doesn't match clientId of box and return status 403" in {
      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId2)
      primeAuthServiceSuccess(clientId2, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
      await(repo.createBox(clientManagedBox))
      val deleteResult = callDeleteClientManagedBoxEndpoint(clientManagedBox.boxId, validHeadersWithAcceptHeader)
      deleteResult.status shouldBe FORBIDDEN
    }
  }

  "PUT /cmb/box/{boxId}/callback" should {
    val callbackUrl = wireMockBaseUrlAsString + "/callback"
    val boxId: BoxId = BoxId.random
    val clientId: ClientId = ClientId.random
    val clientManagedBox: Box = Box(boxName = "boxName", boxId = boxId, boxCreator = BoxCreator(clientId), clientManaged = true)
    def updateCallbackUrlRequestJson() = raw"""{"callbackUrl": "$callbackUrl"}"""

    def updateCallbackUrlRequestJsonNoCallBack(): String = raw"""{"callbackUrl": ""}"""

    "return 200 with {successful:true} and update box successfully when Callback Url is validated" in {
      primeDestinationServiceForValidation(Seq("challenge" -> expectedChallenge), OK, Some(Json.obj("challenge" -> expectedChallenge)))
      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")

      await(repo.createBox(clientManagedBox))

      val updateResult = callClientManagedUpdateCallbackUrlEndpoint(boxId, updateCallbackUrlRequestJson(), validHeadersWithAcceptHeader)
      updateResult.status shouldBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful shouldBe true

      val updatedBox = await(repo.findByBoxId(boxId))
      updatedBox.get.subscriber.get.asInstanceOf[PushSubscriber].callBackUrl shouldBe callbackUrl
    }

    "return 200 with {successful:true} and update box successfully when Callback Url is empty" in {

      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
      await(repo.createBox(clientManagedBox))

      val updateResult = callClientManagedUpdateCallbackUrlEndpoint(boxId, updateCallbackUrlRequestJsonNoCallBack(), validHeadersWithAcceptHeader)
      updateResult.status shouldBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful shouldBe true

      val updatedBox = await(repo.findByBoxId(boxId))
      updatedBox.get.subscriber.get.asInstanceOf[PullSubscriber].callBackUrl.isEmpty shouldBe true
    }

    "return 200 with {successful:false} when Callback Url cannot be validated" in {
      val errorMessage = "Unable to verify callback url"
      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
      primeGatewayServiceValidateCallBack(OK, successfulResult = false, Some(errorMessage))

      await(repo.createBox(clientManagedBox))

      val updateResult = callClientManagedUpdateCallbackUrlEndpoint(boxId, updateCallbackUrlRequestJson(), validHeadersWithAcceptHeader)
      updateResult.status shouldBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful shouldBe false
      responseBody.errorMessage shouldBe Some(errorMessage)
    }

    "return 403 when Box isn't client managed" in {
      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")

      await(repo.createBox(clientManagedBox.copy(clientManaged = false)))

      val updateResult = callClientManagedUpdateCallbackUrlEndpoint(boxId, updateCallbackUrlRequestJson(), validHeadersWithAcceptHeader)

      updateResult.status shouldBe FORBIDDEN
    }

    "return 403 when clientId does not match that on the Box" in {
      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId2)
      primeAuthServiceSuccess(clientId2, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")

      await(repo.createBox(clientManagedBox))

      val updateResult = callClientManagedUpdateCallbackUrlEndpoint(boxId, updateCallbackUrlRequestJson(), validHeadersWithAcceptHeader)

      updateResult.status shouldBe FORBIDDEN
    }

    "return 404 when box does not exist" in {

      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
      val updateResult = callClientManagedUpdateCallbackUrlEndpoint(BoxId.random, updateCallbackUrlRequestJson(), validHeadersWithAcceptHeader)
      updateResult.status shouldBe NOT_FOUND
      updateResult.body shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"
    }
    // TODO check JSON as cant have invalid UUID
//    "return 400 when boxId is not a UUID" in {
//
//      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId.value)
//      primeAuthServiceSuccess(clientId.value, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
//      val updateResult = callClientManagedUpdateCallbackUrlEndpoint("NotaUUid", updateCallbackUrlRequestJson(), validHeadersWithAcceptHeader)
//      updateResult.status shouldBe BAD_REQUEST
//      updateResult.body shouldBe "{\"code\":\"BAD_REQUEST\",\"message\":\"Box ID is not a UUID\"}"
//    }

    "return 400 when requestBody is not a valid payload" in {
      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")

      val updateResult = callClientManagedUpdateCallbackUrlEndpoint(BoxId.random, "{}", validHeadersWithAcceptHeader)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }

    "return 400 when requestBody is not a valid payload against expected format" in {

      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
      val updateResult = callClientManagedUpdateCallbackUrlEndpoint(BoxId.random, "{\"foo\":\"bar\"}", validHeadersWithAcceptHeader)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }

    "return 400 when requestBody is missing" in {

      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      primeAuthServiceSuccess(clientId, "{\"authorise\" : [ ], \"retrieve\" : [ \"clientId\" ]}")
      val updateResult = callClientManagedUpdateCallbackUrlEndpoint(BoxId.random, "", validHeadersWithAcceptHeader)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }
  }

  "POST /cmb/validate" should {
    def validateBody(boxId: BoxId, clientId: ClientId): String = s"""{"boxId":"${boxId.value.toString}","clientId":"${clientId.value}"}"""

    "return 200 {valid: true} when boxId matches clientId" in {
      val createdBox = createBoxAndCheckExistsWithNoSubscribers()
      val response = callValidateBoxEndpoint(validateBody(createdBox.boxId, clientId), validHeadersWithAcceptHeader)
      response.status shouldBe OK

      val responseBody = Json.parse(response.body).as[ValidateBoxOwnershipResponse]
      responseBody.valid shouldBe true
    }

    "return 200 {valid: false} when boxId does not match clientId" in {
      val createdBox = createBoxAndCheckExistsWithNoSubscribers()
      val response = callValidateBoxEndpoint(validateBody(createdBox.boxId, clientId2), validHeadersWithAcceptHeader)
      response.status shouldBe OK

      val responseBody = Json.parse(response.body).as[ValidateBoxOwnershipResponse]
      responseBody.valid shouldBe false
    }

    "return 404 when boxId does not exist" in {
      val response = callValidateBoxEndpoint(validateBody(BoxId.random, clientId), validHeadersWithAcceptHeader)
      response.status shouldBe NOT_FOUND
      response.body shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"
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

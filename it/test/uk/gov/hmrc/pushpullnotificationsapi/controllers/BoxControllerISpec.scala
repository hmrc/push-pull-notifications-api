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

import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers._
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.BoxFormat.boxFormats
import uk.gov.hmrc.pushpullnotificationsapi.services.ChallengeGenerator
import uk.gov.hmrc.pushpullnotificationsapi.support.{AuthService, CallbackDestinationService, ServerBaseISpec, ThirdPartyApplicationService}
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

class BoxControllerISpec
    extends ServerBaseISpec
    with AuthService
    with BeforeAndAfterEach
    with PlayMongoRepositorySupport[Box]
    with CleanMongoCollectionSupport
    with IntegrationPatience
    with CallbackDestinationService
    with ThirdPartyApplicationService
    with ApplicationWithCollaboratorsFixtures
    with TestData {
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
        "microservice.services.third-party-application.port" -> wireMockPort,
        "validateHttpsCallbackUrl" -> false
      ).overrides(bind[ChallengeGenerator].to(stubbedChallengeGenerator))

  val url = s"http://localhost:$port"

  val createBoxJsonBody = raw"""{"clientId": "${clientId.value}", "boxName": "$boxName"}"""
  val createBox2JsonBody = raw"""{"clientId":  "${clientIdTwo.value}", "boxName": "bbyybybyb"}"""
  val expectedChallenge = randomUUID.toString
  val tpaResponse = Json.toJson(standardApp.modify(_.copy(clientId = clientId))).toString()

  val updateSubscriberJsonBodyWithIds: String =
    raw"""{ "subscriber":
         |  {
         |     "subscriberType": "API_PUSH_SUBSCRIBER",
         |     "callBackUrl":"somepath/firstOne"
         |  }
         |}
         |""".stripMargin

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def callCreateBoxEndpoint(jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def callUpdateSubscriberEndpoint(boxId: BoxId, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box/$boxId/subscriber")
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def callUpdateCallbackUrlEndpoint(boxId: BoxId, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/box/$boxId/callback")
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

  def callValidateBoxEndpoint(jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url/cmb/validate")
      .withHttpHeaders(headers: _*)
      .post(jsonBody)
      .futureValue

  // need to clean down mongo then run two

  "BoxController" when {
    "POST /box" should {
      "respond with 201 when box created" in {

        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        val result = callCreateBoxEndpoint(createBoxJsonBody, validHeadersJson.toList)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 200 with box ID  when box already exists" in {
        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        val result1 = callCreateBoxEndpoint(createBoxJsonBody, validHeadersJson.toList)
        validateStringIsUUID(result1.body)

        val result2 = callCreateBoxEndpoint(createBoxJsonBody, validHeadersJson.toList)
        result2.status shouldBe OK
        validateStringIsUUID(result2.body)
        result2.body shouldBe result1.body
      }

      "respond with 201 when two boxs are created" in {
        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
        val result = callCreateBoxEndpoint(createBoxJsonBody, validHeadersJson.toList)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)

        primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientIdTwo)
        val result2 = callCreateBoxEndpoint(createBox2JsonBody, validHeadersJson.toList)
        result2.status shouldBe CREATED
        validateStringIsUUID(result2.body)
      }

      "respond with 400 when NonJson is sent" in {
        val result = callCreateBoxEndpoint("nonJsonPayload", validHeadersJson.toList)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 400 when invalid Json is sent" in {
        val result = callCreateBoxEndpoint("{}", validHeadersJson.toList)
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
          .withHttpHeaders(validHeadersJson.toSeq: _*)
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
      val result = callCreateBoxEndpoint(createBoxJsonBody, validHeadersJson.toList)
      result.status shouldBe CREATED
      validateStringIsUUID(result.body)

      val result2 = callGetBoxByNameAndClientIdEndpoint(boxName, clientId, validHeadersJson.toList)
      result2.status shouldBe OK

      val box = Json.parse(result2.body).as[Box]
      box.boxName shouldBe boxName
      box.boxCreator.clientId shouldBe clientId

    }

    "respond with 404 when empty client id provided" in {

      primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)
      val result = callCreateBoxEndpoint(createBoxJsonBody, validHeadersJson.toList)
      result.status shouldBe CREATED
      validateStringIsUUID(result.body)

      val result2 = callGetBoxByNameAndEmptyClientIdEndpoint(boxName, validHeadersJson.toList)
      result2.status shouldBe NOT_FOUND
      result2.body shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"

    }

    "respond with 404 when box does not exists" in {
      val result = callGetBoxByNameAndClientIdEndpoint(boxName, clientId, validHeadersJson.toList)
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

    "return 200 with {successful:true} and update box successfully when Callback Url is validated" in {
      primeDestinationServiceForCallbackValidation(Seq("challenge" -> expectedChallenge), OK, Some(Json.obj("challenge" -> expectedChallenge)))

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId, updateCallbackUrlRequestJson(clientId), validHeadersJson.toList)
      updateResult.status shouldBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful shouldBe true

      val updatedBox = await(repo.findByBoxId(createdBox.boxId))
      updatedBox.get.subscriber.get.asInstanceOf[PushSubscriber].callBackUrl shouldBe callbackUrl
    }

    "return 200 with {successful:true} and update box successfully when Callback Url is empty" in {

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId, updateCallbackUrlRequestJsonNoCallBack(clientId), validHeadersJson.toList)
      updateResult.status shouldBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful shouldBe true

      val updatedBox = await(repo.findByBoxId(createdBox.boxId))
      updatedBox.get.subscriber.get.asInstanceOf[PullSubscriber].callBackUrl.isEmpty shouldBe true
    }

    "return 401 when useragent header is missing" in {
      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId, updateCallbackUrlRequestJson(clientId), List("Content-Type" -> "application/json"))
      updateResult.status shouldBe FORBIDDEN
    }

    "return 200 with {successful:false} when Callback Url cannot be validated" in {
      primeDestinationServiceForCallbackValidation(Seq("challenge" -> expectedChallenge), OK, Some(Json.obj("challenge" -> "bad challenge")))

      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId, updateCallbackUrlRequestJson(clientId), validHeadersJson.toList)
      updateResult.status shouldBe OK

      val responseBody = Json.parse(updateResult.body).as[UpdateCallbackUrlResponse]
      responseBody.successful shouldBe false
      responseBody.errorMessage shouldBe Some("Invalid callback URL. Check the information you have provided is correct.")
    }

    "return 401 when clientId does not match that on the Box" in {
      val createdBox = createBoxAndCheckExistsWithNoSubscribers()

      val updateResult = callUpdateCallbackUrlEndpoint(createdBox.boxId, updateCallbackUrlRequestJson(ClientId.random), validHeadersJson.toList)

      updateResult.status shouldBe UNAUTHORIZED
    }

    "return 404 when box does not exist" in {
      val updateResult = callUpdateCallbackUrlEndpoint(BoxId.random, updateCallbackUrlRequestJson(clientId), validHeadersJson.toList)
      updateResult.status shouldBe NOT_FOUND
      updateResult.body shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"
    }

    "return 400 when requestBody is not a valid payload" in {
      val updateResult = callUpdateCallbackUrlEndpoint(BoxId.random, "{}", validHeadersJson.toList)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }

    "return 400 when requestBody is not a valid payload against expected format" in {
      val updateResult = callUpdateCallbackUrlEndpoint(BoxId.random, "{\"foo\":\"bar\"}", validHeadersJson.toList)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }

    "return 400 when requestBody is missing" in {
      val updateResult = callUpdateCallbackUrlEndpoint(BoxId.random, "", validHeadersJson.toList)
      updateResult.status shouldBe BAD_REQUEST
      updateResult.body shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"JSON body is invalid against expected format\"}"
    }
  }

  private def createBoxAndCheckExistsWithNoSubscribers(): Box = {
    primeApplicationQueryEndpoint(Status.OK, tpaResponse, clientId)

    val result = callCreateBoxEndpoint(createBoxJsonBody, validHeadersJson.toList)
    result.status shouldBe CREATED
    validateStringIsUUID(result.body)

    val result2 = callGetBoxByNameAndClientIdEndpoint(boxName, clientId, validHeadersJson.toList)
    result2.status shouldBe OK
    val box = Json.parse(result2.body).as[Box]
    box.subscriber.isDefined shouldBe false
    box
  }

}

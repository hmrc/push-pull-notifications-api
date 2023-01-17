/*
 * Copyright 2023 HM Revenue & Customs
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

import java.util.UUID
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import org.mockito.Mockito.verifyNoInteractions
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.HeaderNames.{ACCEPT, CONTENT_TYPE, USER_AGENT}
import play.api.http.Status.NO_CONTENT
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers.{BAD_REQUEST, route, _}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters.boxFormats
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.services.BoxService

class BoxControllerSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with BeforeAndAfterEach {

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockAppConfig: AppConfig = mock[AppConfig]
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockBoxService: BoxService = mock[BoxService]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[BoxService].to(mockBoxService))
    .overrides(bind[AppConfig].to(mockAppConfig))
    .overrides(bind[AuthConnector].to(mockAuthConnector))
    .build()

  override def beforeEach(): Unit = {
    reset(mockBoxService, mockAppConfig, mockAuthConnector)
  }

  private def setUpAppConfig(userAgents: List[String]): Unit = {
    when(mockAppConfig.allowlistedUserAgentList).thenReturn(userAgents)
  }

  val clientIdStr: String = UUID.randomUUID().toString
  val clientId: ClientId = ClientId(clientIdStr)
  val boxName: String = "boxName"

  val boxIdStr: String = UUID.randomUUID().toString
  val boxId: BoxId = BoxId(UUID.fromString(boxIdStr))
  val box: Box = Box(boxId, boxName, BoxCreator(clientId))

  val jsonBody: String =
    raw"""{"boxName": "$boxName",
         |"clientId": "$clientIdStr" }""".stripMargin

  def emptyJsonBody(boxNameVal: String = boxName, clientIdVal: String = clientIdStr): String =
    raw"""{"boxName": "$boxNameVal",
         |"clientId": "$clientIdVal" }""".stripMargin

  private val validAcceptHeader = ACCEPT -> "application/vnd.hmrc.1.0+json"
  private val invalidAcceptHeader = ACCEPT -> "application/vnd.hmrc.2.0+json"
  private val validContentTypeHeader = CONTENT_TYPE -> "application/json"
  private val invalidContentTypeHeader = CONTENT_TYPE -> "text/xml"
  private val emptyContentTypeHeader = CONTENT_TYPE -> ""

  private val validHeadersWithValidUserAgent: Map[String, String] = Map(CONTENT_TYPE -> "application/json", USER_AGENT -> "api-subscription-fields")

  private val validHeadersWithInValidUserAgent: Map[String, String] = Map(validContentTypeHeader, USER_AGENT -> "some-other-service")
  private val validHeadersWithInValidContentType: Map[String, String] = Map(invalidContentTypeHeader, USER_AGENT -> "api-subscription-fields")
  private val validHeadersWithEmptyContentType: Map[String, String] = Map(emptyContentTypeHeader, USER_AGENT -> "api-subscription-fields")
  private val validHeaders: Map[String, String] = Map(validContentTypeHeader, validAcceptHeader)
  private val validHeadersJson: Map[String, String] = Map(validAcceptHeader, validContentTypeHeader)
  private val validHeadersWithInvalidAcceptHeader: Map[String, String] = Map(invalidAcceptHeader, validContentTypeHeader)
  private val validHeadersWithAcceptHeader = List(USER_AGENT -> "api-subscription-fields", ACCEPT -> "application/vnd.hmrc.1.0+json")
  "BoxController" when {
    "createBox" should {
      "return 201 and boxId when box successfully created" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreatedResult(box)))
        val result = doPut("/box", validHeadersWithValidUserAgent, jsonBody)
        status(result) should be(CREATED)
        val expectedBodyStr = s"""{"boxId":"${boxId.value}"}"""
        contentAsJson(result) should be(Json.parse(expectedBodyStr))

        verify(mockBoxService).createBox(eqTo(clientId), eqTo(boxName), eqTo(false))(*, *)
      }

      "return 200 and boxId when box already exists" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxRetrievedResult(box)))
        val result = doPut("/box", validHeadersWithValidUserAgent, jsonBody)
        status(result) should be(OK)
        val expectedBodyStr = s"""{"boxId":"${boxId.value}"}"""
        contentAsJson(result) should be(Json.parse(expectedBodyStr))

        verify(mockBoxService).createBox(eqTo(clientId), eqTo(boxName), eqTo(false))(*, *)
      }

      "return 400 when payload is completely invalid against expected format" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreatedResult(box)))
        val result = doPut("/box", validHeadersWithValidUserAgent, "{\"someOtherJson\":\"value\"}")
        status(result) should be(BAD_REQUEST)
        val expectedBodyStr = s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}"""
        contentAsJson(result) should be(Json.parse(expectedBodyStr))

        verifyNoInteractions(mockBoxService)
      }

      "return 400 when request payload is missing boxName" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreatedResult(box)))
        val result = doPut("/box", validHeadersWithValidUserAgent, emptyJsonBody(boxNameVal = ""))
        status(result) should be(BAD_REQUEST)
        val expectedBodyStr = s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"Expecting boxName and clientId in request body"}"""
        contentAsJson(result) should be(Json.parse(expectedBodyStr))

        verifyNoInteractions(mockBoxService)
      }

      "return 415 when content type header is invalid" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreatedResult(box)))

        val result = doPut("/box", validHeadersWithInValidContentType, jsonBody)
        status(result) should be(UNSUPPORTED_MEDIA_TYPE)

        verifyNoInteractions(mockBoxService)
      }

      "return 415 when content type header is empty" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreatedResult(box)))

        val result = doPut("/box", validHeadersWithEmptyContentType, jsonBody)
        status(result) should be(UNSUPPORTED_MEDIA_TYPE)

        verifyNoInteractions(mockBoxService)
      }

      "return 422 when Left returned from Box Service" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreateFailedResult(s"Box with name :$boxName already exists for clientId: $clientId but unable to retrieve")))
        val result = doPut("/box", validHeadersWithValidUserAgent, jsonBody)
        status(result) should be(UNPROCESSABLE_ENTITY)

        verify(mockBoxService).createBox(eqTo(clientId), eqTo(boxName), eqTo(false))(*, *)
      }

      "return 400 when useragent config is empty" in {
        setUpAppConfig(List.empty)
        val result = doPut("/box", validHeadersWithValidUserAgent, jsonBody)
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockBoxService)
      }

      "return 403 when invalid useragent provided" in {
        setUpAppConfig(List("api-subscription-fields"))
        val result = doPut("/box", validHeadersWithInValidUserAgent, jsonBody)
        status(result) should be(FORBIDDEN)

        verifyNoInteractions(mockBoxService)
      }

      "return 403 when no useragent header provided" in {
        setUpAppConfig(List("api-subscription-fields"))
        val result = doPut("/box", validHeaders, jsonBody)
        status(result) should be(FORBIDDEN)

        verifyNoInteractions(mockBoxService)
      }

      "return 500 when service fails with any runtime exception" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.failed(new RuntimeException("some error")))
        val result = doPut("/box", validHeadersWithValidUserAgent, jsonBody)
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockBoxService).createBox(eqTo(clientId), eqTo(boxName), eqTo(false))(*, *)
      }

      "return 400 when non JSon payload sent" in {
        setUpAppConfig(List("api-subscription-fields"))
        val result = doPut("/box", validHeaders, "xxx")
        status(result) should be(BAD_REQUEST)
        verifyNoInteractions(mockBoxService)
      }

      "return 400 when invalid JSon payload sent" in {
        setUpAppConfig(List("api-subscription-fields"))
        val result = doPut("/box", validHeadersWithValidUserAgent, "{}")
        status(result) should be(BAD_REQUEST)
        verifyNoInteractions(mockBoxService)
      }
    }

    "createClientManagedBox" should {

      "return unauthorised if bearer token doesn't contain client ID" in {
        when(mockAuthConnector.authorise[Option[String]](*, *)(*, *)).thenReturn(Future.successful(None))

        val result = doPut("/cmb/box", validHeadersJson, jsonBody)

        status(result) should be(UNAUTHORIZED)
      }

      "return 201 and boxId when box successfully created" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreatedResult(box)))
        val result = doPut("/cmb/box", validHeadersJson, jsonBody)
        status(result) should be(CREATED)
        val expectedBodyStr = s"""{"boxId":"${boxId.value}"}"""
        contentAsJson(result) should be(Json.parse(expectedBodyStr))

        verify(mockBoxService).createBox(eqTo(clientId), eqTo(boxName), eqTo(true))(*, *)
      }

      "return 200 and boxId when box already exists" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxRetrievedResult(box)))
        val result = doPut("/cmb/box", validHeadersJson, jsonBody)
        status(result) should be(OK)
        val expectedBodyStr = s"""{"boxId":"${boxId.value}"}"""
        contentAsJson(result) should be(Json.parse(expectedBodyStr))

        verify(mockBoxService).createBox(eqTo(clientId), eqTo(boxName), eqTo(true))(*, *)
      }

      "return 400 when payload is completely invalid against expected format" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreatedResult(box)))
        val result = doPut("/cmb/box", validHeadersJson, "{\"someOtherJson\":\"value\"}")
        status(result) should be(BAD_REQUEST)
        val expectedBodyStr = s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}"""
        contentAsJson(result) should be(Json.parse(expectedBodyStr))

        verifyNoInteractions(mockBoxService)
      }

      "return 400 when request payload is missing boxName" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreatedResult(box)))
        val result = doPut("/cmb/box", validHeadersJson, s"""{"boxName":""}""")
        status(result) should be(BAD_REQUEST)
        val expectedBodyStr = s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"Expecting boxName in request body"}"""
        contentAsJson(result) should be(Json.parse(expectedBodyStr))

        verifyNoInteractions(mockBoxService)
      }

      "return 406 when accept header is invalid" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreatedResult(box)))

        val result = doPut("/cmb/box", validHeadersWithInvalidAcceptHeader, jsonBody)
        status(result) should be(NOT_ACCEPTABLE)

        verifyNoInteractions(mockBoxService)
      }

      "return 415 when content type header is invalid" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreatedResult(box)))

        val result = doPut("/cmb/box", validHeadersWithInValidContentType, jsonBody)
        status(result) should be(UNSUPPORTED_MEDIA_TYPE)

        verifyNoInteractions(mockBoxService)
      }

      "return 415 when content type header is empty" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreatedResult(box)))

        val result = doPut("/cmb/box", validHeadersWithEmptyContentType, jsonBody)
        status(result) should be(UNSUPPORTED_MEDIA_TYPE)

        verifyNoInteractions(mockBoxService)
      }

      "return 422 when Left returned from Box Service" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.successful(BoxCreateFailedResult(s"Box with name :$boxName already exists for clientId: $clientId but unable to retrieve")))
        val result = doPut("/cmb/box", validHeadersJson, jsonBody)
        status(result) should be(UNPROCESSABLE_ENTITY)

        verify(mockBoxService).createBox(eqTo(clientId), eqTo(boxName), eqTo(true))(*, *)
      }

      "return 500 when service fails with any runtime exception" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.createBox(*[ClientId], *, *)(*, *))
          .thenReturn(Future.failed(new RuntimeException("some error")))
        val result = doPut("/cmb/box", validHeadersJson, jsonBody)
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockBoxService).createBox(eqTo(clientId), eqTo(boxName), eqTo(true))(*, *)
      }

      "return 400 when non JSon payload sent" in {
        primeAuthAction(clientIdStr)

        val result = doPut("/cmb/box", validHeadersJson, "xxx")
        status(result) should be(BAD_REQUEST)
        verifyNoInteractions(mockBoxService)
      }

      "return 400 when invalid JSon payload sent" in {
        primeAuthAction(clientIdStr)

        val result = doPut("/cmb/box", validHeadersJson, "{}")
        status(result) should be(BAD_REQUEST)
        verifyNoInteractions(mockBoxService)
      }
    }

    "deleteClientManagedBox" should {

      "return unauthorised if bearer token doesn't contain client ID on a box delete" in {
        when(mockAuthConnector.authorise[Option[String]](*, *)(*, *)).thenReturn(Future.successful(None))

        val result = doDelete(s"/cmb/box/${boxId.raw}", validHeadersWithAcceptHeader)

        status(result) should be(UNAUTHORIZED)
      }

      "return 201 and boxId when box successfully deleted" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.deleteBox(eqTo(clientId), eqTo(boxId))(*))
          .thenReturn(Future.successful(BoxDeleteSuccessfulResult()))
        val result = doDelete(s"/cmb/box/${boxId.raw}", validHeadersWithAcceptHeader)
        status(result) should be(NO_CONTENT)
      }

      "return 404(NOT FOUND) when attempting to delete a box with an ID that does not exist" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.deleteBox(eqTo(clientId), eqTo(boxId))(*))
          .thenReturn(Future.successful(BoxDeleteNotFoundResult()))
        val result = doDelete(s"/cmb/box/${boxId.raw}", validHeadersWithAcceptHeader)
        status(result) should be(NOT_FOUND)
      }

      "return 403(FORBIDDEN) when Attempt to delete a box which for a different client ID" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.deleteBox(eqTo(clientId), eqTo(boxId))(*))
          .thenReturn(Future.successful(BoxDeleteAccessDeniedResult()))
        val result = doDelete(s"/cmb/box/${boxId.raw}", validHeadersWithAcceptHeader)
        status(result) should be(FORBIDDEN)
      }

      "return 403(FORBIDDEN) Attempt to delete a default box should not be allowed" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.deleteBox(eqTo(clientId), eqTo(boxId))(*))
          .thenReturn(Future.successful(BoxDeleteAccessDeniedResult()))
        val result = doDelete(s"/cmb/box/${boxId.raw}", validHeadersWithAcceptHeader)
        status(result) should be(FORBIDDEN)
      }

      "return 406 when a Incorrect Accept Header Version" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.deleteBox(eqTo(clientId), eqTo(boxId))(*))
          .thenReturn(Future.successful(BoxDeleteAccessDeniedResult()))
        val result = doDelete(s"/cmb/box/${boxId.raw}", validHeadersWithInvalidAcceptHeader.toList)
        status(result) should be(NOT_ACCEPTABLE)
        (contentAsJson(result) \ "code").as[String] shouldBe "ACCEPT_HEADER_INVALID"
        (contentAsJson(result) \ "message").as[String] shouldBe "The accept header is missing or invalid"
      }

      "return 422 when Left returned from Box Service" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.deleteBox(eqTo(clientId), eqTo(boxId))(*))
          .thenReturn(Future.successful(BoxDeleteFailedResult(s"Box with name :$boxName and clientId: $clientId but unable to delete")))
        val result = doDelete(s"/cmb/box/${boxId.raw}", validHeadersWithAcceptHeader.toList)
        status(result) should be(UNPROCESSABLE_ENTITY)

        verify(mockBoxService).deleteBox(eqTo(clientId), eqTo(boxId))(*)
      }

      "return 500 when service fails with any runtime exception" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.deleteBox(eqTo(clientId), eqTo(boxId))(*))
          .thenReturn(Future.failed(new RuntimeException("some error")))
        val result = doDelete(s"/cmb/box/${boxId.raw}", validHeadersWithAcceptHeader.toList)
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockBoxService).deleteBox(eqTo(clientId), eqTo(boxId))(*)
      }
    }

    "getBoxByNameAndClientId" should {

      "return 200 and requested box when it exists" in {

        when(mockBoxService.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(*))
          .thenReturn(Future.successful(Some(Box(boxId = BoxId(UUID.randomUUID()), boxName = boxName, BoxCreator(clientId)))))

        val result = doGet(s"/box?boxName=$boxName&clientId=${clientId.value}", validHeaders)

        status(result) should be(OK)

        verify(mockBoxService).getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(*)
        val bodyVal = Helpers.contentAsString(result)
        val box = Json.parse(bodyVal).as[Box]
        box.subscriber.isDefined shouldBe false
      }

      "return 200 and all boxes when no parameters provided" in {
        val expectedBoxes = List(Box(boxId = BoxId(UUID.randomUUID()), boxName = boxName, BoxCreator(clientId)))
        when(mockBoxService.getAllBoxes()(*))
          .thenReturn(Future.successful(expectedBoxes))

        val result = doGet(s"/box", validHeaders)

        status(result) should be(OK)

        val bodyVal = Helpers.contentAsString(result)
        val actualBoxes = Json.parse(bodyVal).as[List[Box]]

        actualBoxes shouldBe expectedBoxes

        verify(mockBoxService).getAllBoxes()(*)
      }

      "return 400 when boxName is missing" in {
        when(mockBoxService.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(*))
          .thenReturn(Future.successful(Some(Box(boxId = BoxId(UUID.randomUUID()), boxName = boxName, BoxCreator(clientId)))))

        val result = doGet(s"/box?clientId=$clientIdStr", validHeaders)
        status(result) should be(BAD_REQUEST)
        Helpers.contentAsString(result) shouldBe "{\"code\":\"BAD_REQUEST\",\"message\":\"Must specify both boxName and clientId query parameters or neither\"}"
        verifyNoInteractions(mockBoxService)
      }

      "return 400 when clientId is missing" in {
        when(mockBoxService.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(*))
          .thenReturn(Future.successful(Some(Box(boxId = BoxId(UUID.randomUUID()), boxName = boxName, BoxCreator(clientId)))))

        val result = doGet(s"/box?boxName=$boxName", validHeaders)
        status(result) should be(BAD_REQUEST)
        Helpers.contentAsString(result) shouldBe "{\"code\":\"BAD_REQUEST\",\"message\":\"Must specify both boxName and clientId query parameters or neither\"}"
        verifyNoInteractions(mockBoxService)
      }

      "return NOTFOUND when requested box does not exist" in {
        when(mockBoxService.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(*)).thenReturn(Future.successful(None))

        val result = doGet(s"/box?boxName=$boxName&clientId=${clientId.value}", validHeaders)

        status(result) should be(NOT_FOUND)
        Helpers.contentAsString(result) shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"

        verify(mockBoxService).getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))(*)
      }
    }

    "getBoxesByClientId" should {

      "return empty list when client has no boxes" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.getBoxesByClientId(eqTo(clientId))(*)).thenReturn(Future.successful(List()))
        val result = doGet(s"/cmb/box", validHeaders)

        contentAsString(result) shouldBe "[]"

        verify(mockBoxService).getBoxesByClientId(eqTo(clientId))(*)
      }

      "return boxes for client specified in auth token in json format" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.getBoxesByClientId(eqTo(clientId))(*)).thenReturn(Future.successful(List(box)))
        val result = doGet("/cmb/box", validHeaders)
        val expected =
          s"""[{"boxId":"$boxIdStr","boxName":"DEFAULT","boxCreator":{"clientId":"$clientIdStr"},"clientManaged":false}]"""

        contentAsString(result) shouldBe expected

        verify(mockBoxService).getBoxesByClientId(eqTo(clientId))(*)
      }

      "return a 500 response code if service fails with an exception" in {
        primeAuthAction(clientIdStr)

        when(mockBoxService.getBoxesByClientId(eqTo(clientId))(*)).thenReturn(Future.failed(new RuntimeException("some error")))

        val result = doGet("/cmb/box", validHeaders)

        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockBoxService).getBoxesByClientId(eqTo(clientId))(*)
      }

      // All these test cases below should probably be replaced by an assertion that the correct ActionFilters have
      // been called.
      "return unauthorised if bearer token doesn't contain client ID" in {
        when(mockAuthConnector.authorise[Option[String]](*, *)(*, *)).thenReturn(Future.successful(None))

        val result = doGet("/cmb/box", validHeaders)

        status(result) should be(UNAUTHORIZED)
      }

      "return 406 when accept header is missing" in {
        primeAuthAction(clientIdStr)

        val result = doGet("/cmb/box", validHeaders - ACCEPT)

        status(result) shouldBe NOT_ACCEPTABLE
        (contentAsJson(result) \ "code").as[String] shouldBe "ACCEPT_HEADER_INVALID"
        (contentAsJson(result) \ "message").as[String] shouldBe "The accept header is missing or invalid"
      }

      "return 406 when accept header is invalid" in {
        primeAuthAction(clientIdStr)

        val result = doGet("/cmb/box", validHeaders + (ACCEPT -> "XYZAccept"))

        status(result) shouldBe NOT_ACCEPTABLE
        (contentAsJson(result) \ "code").as[String] shouldBe "ACCEPT_HEADER_INVALID"
        (contentAsJson(result) \ "message").as[String] shouldBe "The accept header is missing or invalid"
      }
    }

    "addCallbackUrl" should {

      def createRequest(clientId: String, callBackUrl: String) = {
        raw"""
             |{
             |   "clientId": "$clientId",
             |   "callbackUrl": "$callBackUrl"
             |}
             |""".stripMargin
      }

      "return 200 when request is successful" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackUrlUpdated()))

        val result =
          doPut(
            s"/box/${boxId.value}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe """{"successful":true}"""
      }

      "return 200 when request contains " in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackUrlUpdated()))

        val result =
          doPut(
            s"/box/${boxId.value}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe """{"successful":true}"""
      }

      "return 401 if User-Agent is not allowlisted" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackUrlUpdated()))

        val result =
          doPut(
            s"/box/${boxId.value}/callback",
            validHeaders,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(FORBIDDEN)
        Helpers.contentAsString(result) shouldBe """{"code":"FORBIDDEN","message":"Authorisation failed"}"""
      }

      "return 404 if Box does not exist" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(BoxIdNotFound()))

        val result =
          doPut(
            s"/box/${boxId.value}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(NOT_FOUND)
      }

      "return 200, successful false and errormessage when mongo update fails" in {
        setUpAppConfig(List("api-subscription-fields"))
        val errorMessage = "Unable to update"
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(UnableToUpdateCallbackUrl(errorMessage)))

        val result =
          doPut(
            s"/box/${boxId.value}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe s"""{"successful":false,"errorMessage":"$errorMessage"}"""
      }

      "return 200, successful false and errormessage when callback validation fails" in {
        setUpAppConfig(List("api-subscription-fields"))
        val errorMessage = "Unable to update"
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackValidationFailed(errorMessage)))

        val result =
          doPut(
            s"/box/${boxId.value}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe s"""{"successful":false,"errorMessage":"$errorMessage"}"""
      }

      "return 401 if client id does not match that on the box" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(UpdateCallbackUrlUnauthorisedResult()))

        val result =
          doPut(
            s"/box/${boxId.value}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(UNAUTHORIZED)
      }

      "return 400 when payload is non JSON" in {
        val result = doPut(s"/box/5fc1f8e5-8881-4863-8a8c-5c897bb56815/callback", validHeadersWithValidUserAgent, "someBody")

        status(result) should be(BAD_REQUEST)
        verifyNoInteractions(mockBoxService)
      }

      "return 400 when payload is missing the clientId value" in {
        setUpAppConfig(List("api-subscription-fields"))
        val result =
          doPut(s"/box/5fc1f8e5-8881-4863-8a8c-5c897bb56815/callback", validHeadersWithValidUserAgent, createRequest("", "callbackUrl"))

        status(result) should be(BAD_REQUEST)
        Helpers.contentAsString(result) shouldBe """{"code":"INVALID_REQUEST_PAYLOAD","message":"clientId is required"}"""
        verifyNoInteractions(mockBoxService)
      }

      "return 200 when payload is missing the callbackUrl value" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackUrlUpdated()))
        val result = doPut(s"/box/$boxIdStr/callback", validHeadersWithValidUserAgent, createRequest(clientIdStr, ""))

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe """{"successful":true}"""
        verify(mockBoxService).updateCallbackUrl(eqTo(boxId), *, *)(*, *)
      }
    }

    "updatedManagedBoxCallbackUrl" should {
      def createRequest(callBackUrl: String): String = s"""{"callbackUrl": "$callBackUrl"}"""

      "return 200 when request is successful" in {
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackUrlUpdated()))
        primeAuthAction(clientIdStr)

        val result = doPut(
          s"/cmb/box/${boxId.value}/callback",
          validHeadersJson,
          createRequest("callbackUrl")
        )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe """{"successful":true}"""
      }

      "return 404 if Box does not exist" in {
        primeAuthAction(clientIdStr)
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(BoxIdNotFound()))

        val result = doPut(
          s"/cmb/box/${boxId.value}/callback",
          validHeadersJson,
          createRequest("callbackUrl")
        )

        status(result) should be(NOT_FOUND)
      }

      "return 200, successful false and errormessage when mongo update fails" in {
        primeAuthAction(clientIdStr)
        val errorMessage = "Unable to update"
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(UnableToUpdateCallbackUrl(errorMessage)))

        val result =
          doPut(
            s"/cmb/box/${boxId.value}/callback",
            validHeadersJson,
            createRequest("callbackUrl")
          )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe s"""{"successful":false,"errorMessage":"$errorMessage"}"""
      }

      "return 200, successful false and errormessage when callback validation fails" in {
        primeAuthAction(clientIdStr)
        val errorMessage = "Unable to update"
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackValidationFailed(errorMessage)))

        val result = doPut(
          s"/cmb/box/${boxId.value}/callback",
          validHeadersJson,
          createRequest("callbackUrl")
        )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe s"""{"successful":false,"errorMessage":"$errorMessage"}"""
      }

      "return 403 if client id does not match that on the box" in {
        primeAuthAction(clientIdStr)
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(UpdateCallbackUrlUnauthorisedResult()))

        val result = doPut(
          s"/cmb/box/${boxId.value}/callback",
          validHeadersJson,
          createRequest("callbackUrl")
        )

        status(result) should be(FORBIDDEN)
      }

      "return 400 when payload is non JSON" in {
        val result = doPut(s"/cmb/box/5fc1f8e5-8881-4863-8a8c-5c897bb56815/callback", validHeadersJson, "someBody")

        status(result) should be(BAD_REQUEST)
        verifyNoInteractions(mockBoxService)
      }

      "return 200 when payload is missing the callbackUrl value" in {
        primeAuthAction(clientIdStr)
        when(mockBoxService.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackUrlUpdated()))
        val result = doPut(s"/cmb/box/$boxIdStr/callback", validHeadersJson, createRequest(""))

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe """{"successful":true}"""
        verify(mockBoxService).updateCallbackUrl(eqTo(boxId), *, *)(*, *)
      }
    }

    "validateBoxOwnership" should {
      def validateBody(boxId: String, clientId: String): String = s"""{"boxId":"$boxId","clientId":"$clientId"}"""

      "return 200 and valid when ownership confirmed" in {
        when(mockBoxService.validateBoxOwner(eqTo(boxId), eqTo(clientId))(*))
          .thenReturn(Future.successful(ValidateBoxOwnerSuccessResult()))

        val result = doPost("/cmb/validate", validHeaders, validateBody(boxIdStr, clientIdStr))

        status(result) shouldBe OK
        (contentAsJson(result) \ "valid").as[Boolean] shouldBe true
      }

      "return 200 and invalid when ownership not confirmed" in {
        when(mockBoxService.validateBoxOwner(eqTo(boxId), eqTo(clientId))(*))
          .thenReturn(Future.successful(ValidateBoxOwnerFailedResult("Ownership doesn't match")))

        val result = doPost("/cmb/validate", validHeaders, validateBody(boxIdStr, clientIdStr))

        status(result) shouldBe OK
        (contentAsJson(result) \ "valid").as[Boolean] shouldBe false
      }

      "return 404 when box not found" in {
        when(mockBoxService.validateBoxOwner(eqTo(boxId), eqTo(clientId))(*))
          .thenReturn(Future.successful(ValidateBoxOwnerNotFoundResult("Box not found")))

        val result = doPost("/cmb/validate", validHeaders, validateBody(boxIdStr, clientIdStr))

        status(result) shouldBe NOT_FOUND
        (contentAsJson(result) \ "code").as[String] shouldBe "BOX_NOT_FOUND"
        (contentAsJson(result) \ "message").as[String] shouldBe "Box not found"
      }

      "return 400 when clientId is empty" in {
        val result = doPost("/cmb/validate", validHeaders, validateBody(boxIdStr, ""))

        status(result) shouldBe BAD_REQUEST
        (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_REQUEST_PAYLOAD"
        (contentAsJson(result) \ "message").as[String] shouldBe "Expecting boxId and clientId in request body"
      }

      "return 400 when format is wrong" in {
        val result = doPost("/cmb/validate", validHeaders, s"""{"boxId":"$boxIdStr"}""")

        status(result) shouldBe BAD_REQUEST
        (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_REQUEST_PAYLOAD"
        (contentAsJson(result) \ "message").as[String] shouldBe "JSON body is invalid against expected format"
      }

      "return 406 when accept header is missing" in {
        val result = doPost("/cmb/validate", validHeaders - ACCEPT, validateBody(boxIdStr, clientIdStr))

        status(result) shouldBe NOT_ACCEPTABLE
        (contentAsJson(result) \ "code").as[String] shouldBe "ACCEPT_HEADER_INVALID"
        (contentAsJson(result) \ "message").as[String] shouldBe "The accept header is missing or invalid"
      }

      "return 406 when accept header is invalid" in {
        val result = doPost("/cmb/validate", validHeaders + (ACCEPT -> "XYZAccept"), validateBody(boxIdStr, clientIdStr))

        status(result) shouldBe NOT_ACCEPTABLE
        (contentAsJson(result) \ "code").as[String] shouldBe "ACCEPT_HEADER_INVALID"
        (contentAsJson(result) \ "message").as[String] shouldBe "The accept header is missing or invalid"
      }
    }
  }

  private def primeAuthAction(clientId: String): Unit = {
    when(mockAuthConnector.authorise[Option[String]](*, *)(*, *)).thenReturn(Future.successful(Some(clientId)))
  }

  def doGet(uri: String, headers: Map[String, String]): Future[Result] = {
    val fakeRequest = FakeRequest(GET, uri).withHeaders(headers.toSeq: _*)
    route(app, fakeRequest).get
  }

  def doPost(uri: String, headers: Map[String, String], bodyValue: String): Future[Result] = {
    doWithBody(uri, headers, bodyValue, POST)
  }

  def doPut(uri: String, headers: Map[String, String], bodyValue: String): Future[Result] = {
    doPUT(uri, headers, bodyValue)
  }

  private def doPUT(uri: String, headers: Map[String, String], bodyValue: String): Future[Result] = {
    doWithBody(uri, headers, bodyValue, PUT)
  }

  private def doWithBody(uri: String, headers: Map[String, String], bodyValue: String, method: String) = {
    val maybeBody: Option[JsValue] = Try {
      Json.parse(bodyValue)
    } match {
      case Success(value) => Some(value)
      case Failure(_)     => None
    }

    val fakeRequest = FakeRequest(method, uri).withHeaders(headers.toSeq: _*)
    maybeBody
      .fold(route(app, fakeRequest.withBody(bodyValue)).get)(jsonBody => route(app, fakeRequest.withJsonBody(jsonBody)).get)
  }

  def doDelete(uri: String, headers: List[(String, String)]): Future[Result] = {
    val fakeRequest = FakeRequest(DELETE, uri).withHeaders(headers: _*)
    route(app, fakeRequest).get
  }
}

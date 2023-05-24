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
import play.api.http.Status
import play.api.http.Status.NO_CONTENT
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers.{BAD_REQUEST, route, status, _}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.mocks.BoxServiceMockModule
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters.boxFormats
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.services.BoxService
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

class BoxControllerSpec extends AsyncHmrcSpec with BoxServiceMockModule
  with TestData with GuiceOneAppPerSuite with BeforeAndAfterEach {

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockAppConfig: AppConfig = mock[AppConfig]
  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[BoxService].to(BoxServiceMock.aMock))
    .overrides(bind[AppConfig].to(mockAppConfig))
    .overrides(bind[AuthConnector].to(mockAuthConnector))
    .build()

  override def beforeEach(): Unit = {
    reset(BoxServiceMock.aMock, mockAppConfig, mockAuthConnector)
  }

  private def setUpAppConfig(userAgents: List[String]): Unit = {
    when(mockAppConfig.allowlistedUserAgentList).thenReturn(userAgents)
  }


  val jsonBody: String =
    raw"""{"boxName": "$boxName",
         |"clientId": "${clientId.value}" }""".stripMargin

  def emptyJsonBody(boxNameVal: String = boxName, clientIdVal: String = clientId.value): String =
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
    def validateResult(result: Future[Result], expectedStatus: Int, expectedBodyStr: String) = {
        status(result) should be(expectedStatus)

        expectedStatus match {
          case INTERNAL_SERVER_ERROR => succeed
          case NO_CONTENT => succeed
          case _ =>  contentAsJson(result) should be(Json.parse(expectedBodyStr))
        }
    }

    "createBox" should {

      "return 201 and boxId when box successfully created" in {
        setUpAppConfig(List("api-subscription-fields"))
        BoxServiceMock.CreateBox.thenSucceedCreated(box)

        validateResult(doPut("/box", validHeadersWithValidUserAgent, jsonBody), CREATED,
          s"""{"boxId":"${boxId.value.toString}"}""")

        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName, isClientManaged = false)
      }

      "return 200 and boxId when box already exists" in {
        setUpAppConfig(List("api-subscription-fields"))

        BoxServiceMock.CreateBox.thenSucceedRetrieved(box)

        validateResult(doPut("/box", validHeadersWithValidUserAgent, jsonBody), OK,
          s"""{"boxId":"${boxId.value.toString}"}""")

        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName, isClientManaged = false)
      }

      "return 400 when payload is completely invalid against expected format" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(doPut("/box", validHeadersWithValidUserAgent, "{\"someOtherJson\":\"value\"}"),
          BAD_REQUEST,
          s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 400 when request payload is missing boxName" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(doPut("/box", validHeadersWithValidUserAgent, emptyJsonBody(boxNameVal = "")),
          BAD_REQUEST,
          s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"Expecting boxName and clientId in request body"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 415 when content type header is invalid" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(doPut("/box", validHeadersWithInValidContentType, jsonBody),
          UNSUPPORTED_MEDIA_TYPE,
          s"""{"code":"BAD_REQUEST","message":"Expecting text/json or application/json body"}""")


        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 415 when content type header is empty" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(doPut("/box", validHeadersWithEmptyContentType, jsonBody),
          UNSUPPORTED_MEDIA_TYPE,
          s"""{"code":"BAD_REQUEST","message":"Expecting text/json or application/json body"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 422 when Left returned from Box Service" in {
        setUpAppConfig(List("api-subscription-fields"))
        BoxServiceMock.CreateBox.thenFailsWithBoxName(boxName, clientId)

        validateResult(doPut("/box", validHeadersWithValidUserAgent, jsonBody),
          UNPROCESSABLE_ENTITY,
          s"""{"code":"UNKNOWN_ERROR","message":"unable to createBox:Box with name :$boxName already exists for clientId: ${clientId.value} but unable to retrieve" }""")

        verify(BoxServiceMock.aMock).createBox(eqTo(clientId), eqTo(boxName), eqTo(false))(*, *)
      }

      "return 400 when useragent config is empty" in {
        setUpAppConfig(List.empty)

        validateResult(doPut("/box", validHeadersWithValidUserAgent, jsonBody),
          INTERNAL_SERVER_ERROR,
          s"""{}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 403 when invalid useragent provided" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(doPut("/box", validHeadersWithInValidUserAgent, jsonBody),
          FORBIDDEN,
          s"""{"code":"FORBIDDEN","message":"Authorisation failed"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 403 when no useragent header provided" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(doPut("/box", validHeaders, jsonBody),
          FORBIDDEN,
          s"""{"code":"FORBIDDEN","message":"Authorisation failed"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 500 when service fails with any runtime exception" in {
        setUpAppConfig(List("api-subscription-fields"))

        BoxServiceMock.CreateBox.thenFailsWithException("some error")

        validateResult(doPut("/box", validHeadersWithValidUserAgent, jsonBody),
          INTERNAL_SERVER_ERROR,
          "")

        verify(BoxServiceMock.aMock).createBox(eqTo(clientId), eqTo(boxName), eqTo(false))(*, *)
      }

      "return 400 when non JSon payload sent" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(doPut("/box", validHeadersWithValidUserAgent, "xxx"),
          BAD_REQUEST,
          """{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 400 when invalid JSon payload sent" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(doPut("/box", validHeadersWithValidUserAgent, "{}"),
          BAD_REQUEST,
          """{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }
    }

    "createClientManagedBox" should {

      "return unauthorised if bearer token doesn't contain client ID" in {
        when(mockAuthConnector.authorise[Option[String]](*, *)(*, *)).thenReturn(Future.successful(None))

        validateResult(doPut("/cmb/box", validHeadersJson, jsonBody),
          expectedStatus = UNAUTHORIZED,
          expectedBodyStr = s"""{"code":"UNAUTHORISED","message":"Unable to retrieve ClientId"}""")

      }

      "return 201 and boxId when box successfully created" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.CreateBox.thenSucceedCreated(box)

        validateResult(doPut("/cmb/box", validHeadersJson, jsonBody),
          expectedStatus = CREATED,
          expectedBodyStr = s"""{"boxId":"${boxId.value.toString}"}""")

        verify(BoxServiceMock.aMock).createBox(eqTo(clientId), eqTo(boxName), eqTo(true))(*, *)
      }

      "return 200 and boxId when box already exists" in {
        primeAuthAction(clientId.value)
        BoxServiceMock.CreateBox.thenSucceedRetrieved(box)

        validateResult(doPut("/cmb/box", validHeadersJson, jsonBody),
          expectedStatus = OK,
          expectedBodyStr = s"""{"boxId":"${boxId.value.toString}"}""")

        verify(BoxServiceMock.aMock).createBox(eqTo(clientId), eqTo(boxName), eqTo(true))(*, *)
      }

      "return 400 when payload is completely invalid against expected format" in {
        primeAuthAction(clientId.value)

        validateResult(doPut("/cmb/box", validHeadersJson, "{\"someOtherJson\":\"value\"}"),
          expectedStatus = BAD_REQUEST,
          expectedBodyStr = s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 400 when request payload is missing boxName" in {
        primeAuthAction(clientId.value)

        validateResult(doPut("/cmb/box", validHeadersJson, s"""{"boxName":""}"""),
          expectedStatus = BAD_REQUEST,
          expectedBodyStr = s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"Expecting boxName in request body"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 406 when accept header is invalid" in {
        primeAuthAction(clientId.value)

        validateResult(doPut("/cmb/box", validHeadersWithInvalidAcceptHeader, jsonBody),
          expectedStatus = NOT_ACCEPTABLE,
          expectedBodyStr = s"""{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 415 when content type header is invalid" in {
        primeAuthAction(clientId.value)

        validateResult(doPut("/cmb/box", validHeadersWithInValidContentType, jsonBody),
          expectedStatus = UNSUPPORTED_MEDIA_TYPE,
          expectedBodyStr = s"""{"code":"BAD_REQUEST","message":"Expecting text/json or application/json body"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 415 when content type header is empty" in {
        primeAuthAction(clientId.value)

        validateResult(doPut("/cmb/box", validHeadersWithEmptyContentType, jsonBody),
          expectedStatus = UNSUPPORTED_MEDIA_TYPE,
          expectedBodyStr = s"""{"code":"BAD_REQUEST","message":"Expecting text/json or application/json body"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 422 when Left returned from Box Service" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.CreateBox.thenFailsWithCreateFailedResult("some error")

        validateResult(doPut("/cmb/box", validHeadersJson, jsonBody),
          expectedStatus = UNPROCESSABLE_ENTITY,
          expectedBodyStr = s"""{"code":"UNKNOWN_ERROR","message":"unable to createBox:some error"}""")

        verify(BoxServiceMock.aMock).createBox(eqTo(clientId), eqTo(boxName), eqTo(true))(*, *)
      }

      "return 500 when service fails with any runtime exception" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.CreateBox.thenFailsWithException("some error")

        validateResult(doPut("/cmb/box", validHeadersJson, jsonBody),
          expectedStatus = INTERNAL_SERVER_ERROR,
          expectedBodyStr = "")

        verify(BoxServiceMock.aMock).createBox(eqTo(clientId), eqTo(boxName), eqTo(true))(*, *)
      }

      "return 400 when non JSon payload sent" in {
        primeAuthAction(clientId.value)


        validateResult(doPut("/cmb/box", validHeadersJson, "xxx"),
          expectedStatus = BAD_REQUEST,
          expectedBodyStr = """{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 400 when invalid JSon payload sent" in {
        primeAuthAction(clientId.value)

        validateResult(doPut("/cmb/box", validHeadersJson, "{}"),
          expectedStatus = BAD_REQUEST,
          expectedBodyStr = """{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}""")

        verifyNoInteractions(BoxServiceMock.aMock)
      }
    }

    "deleteClientManagedBox" should {

      "return unauthorised if bearer token doesn't contain client ID on a box delete" in {
        when(mockAuthConnector.authorise[Option[String]](*, *)(*, *)).thenReturn(Future.successful(None))

        validateResult(doDelete(s"/cmb/box/${boxId.value.toString}", validHeadersWithAcceptHeader),
          expectedStatus = UNAUTHORIZED,
          expectedBodyStr = """{"code":"UNAUTHORISED","message":"Unable to retrieve ClientId"}""")

        BoxServiceMock.verifyZeroInteractions()
      }

      "return 201 and boxId when box successfully deleted" in {
        primeAuthAction(clientId.value)
        BoxServiceMock.DeleteBox.isSuccessful()

        validateResult(doDelete(s"/cmb/box/${boxId.value.toString}", validHeadersWithAcceptHeader),
          expectedStatus = NO_CONTENT,
          expectedBodyStr = "")

        BoxServiceMock.DeleteBox.verifyCalledWith(clientId, boxId)
      }

      "return 404(NOT FOUND) when attempting to delete a box with an ID that does not exist" in {
        primeAuthAction(clientId.value)
        BoxServiceMock.DeleteBox.failsNotFound()

        validateResult(doDelete(s"/cmb/box/${boxId.value.toString}", validHeadersWithAcceptHeader),
          expectedStatus = NOT_FOUND,
          expectedBodyStr = """{"code":"BOX_NOT_FOUND","message":"Box not found"}""")

        BoxServiceMock.DeleteBox.verifyCalledWith(clientId, boxId)
      }

      "return 403(FORBIDDEN) when Attempt to delete a box which for a different client ID" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.DeleteBox.failsAccessDenied()

        validateResult(doDelete(s"/cmb/box/${boxId.value.toString}", validHeadersWithAcceptHeader),
          expectedStatus = FORBIDDEN,
          expectedBodyStr = """{"code":"FORBIDDEN","message":"Access denied"}""")

        BoxServiceMock.DeleteBox.verifyCalledWith(clientId, boxId)
      }

      "return 403(FORBIDDEN) Attempt to delete a default box should not be allowed" in {
        primeAuthAction(clientId.value)

        when(BoxServiceMock.aMock.deleteBox(eqTo(clientId), eqTo(boxId))(*))
          .thenReturn(Future.successful(BoxDeleteAccessDeniedResult()))
        val result = doDelete(s"/cmb/box/${boxId.value.toString.toString}", validHeadersWithAcceptHeader)
        status(result) should be(FORBIDDEN)

        BoxServiceMock.DeleteBox.verifyCalledWith(clientId, boxId)
      }

      "return 406 when a Incorrect Accept Header Version" in {
        primeAuthAction(clientId.value)

        when(BoxServiceMock.aMock.deleteBox(eqTo(clientId), eqTo(boxId))(*))
          .thenReturn(Future.successful(BoxDeleteAccessDeniedResult()))
        val result = doDelete(s"/cmb/box/${boxId.value.toString.toString}", validHeadersWithInvalidAcceptHeader.toList)
        status(result) should be(NOT_ACCEPTABLE)
        (contentAsJson(result) \ "code").as[String] shouldBe "ACCEPT_HEADER_INVALID"
        (contentAsJson(result) \ "message").as[String] shouldBe "The accept header is missing or invalid"

        BoxServiceMock.DeleteBox.verifyCalledWith(clientId, boxId)
      }

      "return 422 when Left returned from Box Service" in {
        primeAuthAction(clientId.value)

        when(BoxServiceMock.aMock.deleteBox(eqTo(clientId), eqTo(boxId))(*))
          .thenReturn(Future.successful(BoxDeleteFailedResult(s"Box with name :$boxName and clientId: $clientId but unable to delete")))
        val result = doDelete(s"/cmb/box/${boxId.value.toString.toString}", validHeadersWithAcceptHeader.toList)
        status(result) should be(UNPROCESSABLE_ENTITY)

        BoxServiceMock.DeleteBox.verifyCalledWith(clientId, boxId)
      }

      "return 500 when service fails with any runtime exception" in {
        primeAuthAction(clientId.value)

        when(BoxServiceMock.aMock.deleteBox(eqTo(clientId), eqTo(boxId))(*))
          .thenReturn(Future.failed(new RuntimeException("some error")))
        val result = doDelete(s"/cmb/box/${boxId.value.toString.toString}", validHeadersWithAcceptHeader.toList)
        status(result) should be(INTERNAL_SERVER_ERROR)

        BoxServiceMock.DeleteBox.verifyCalledWith(clientId, boxId)
      }
    }

    "getBoxByNameAndClientId" should {

      "return 200 and requested box when it exists" in {

        when(BoxServiceMock.aMock.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId)))
          .thenReturn(Future.successful(Some(Box(boxId = BoxId(UUID.randomUUID()), boxName = boxName, BoxCreator(clientId)))))

        val result = doGet(s"/box?boxName=$boxName&clientId=${clientId.value}", validHeaders)

        status(result) should be(OK)

        verify(BoxServiceMock.aMock).getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))
        val bodyVal = Helpers.contentAsString(result)
        val box = Json.parse(bodyVal).as[Box]
        box.subscriber.isDefined shouldBe false
      }

      "return 200 and all boxes when no parameters provided" in {
        val expectedBoxes = List(Box(boxId = BoxId(UUID.randomUUID()), boxName = boxName, BoxCreator(clientId)))
        when(BoxServiceMock.aMock.getAllBoxes()(*))
          .thenReturn(Future.successful(expectedBoxes))

        val result = doGet(s"/box", validHeaders)

        status(result) should be(OK)

        val bodyVal = Helpers.contentAsString(result)
        val actualBoxes = Json.parse(bodyVal).as[List[Box]]

        actualBoxes shouldBe expectedBoxes

        verify(BoxServiceMock.aMock).getAllBoxes()(*)
      }

      "return 400 when boxName is missing" in {
        when(BoxServiceMock.aMock.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId)))
          .thenReturn(Future.successful(Some(Box(boxId = BoxId(UUID.randomUUID()), boxName = boxName, BoxCreator(clientId)))))

        val result = doGet(s"/box?clientId=$clientId.value", validHeaders)
        status(result) should be(BAD_REQUEST)
        Helpers.contentAsString(result) shouldBe "{\"code\":\"BAD_REQUEST\",\"message\":\"Must specify both boxName and clientId query parameters or neither\"}"
        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 400 when clientId is missing" in {
        when(BoxServiceMock.aMock.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId)))
          .thenReturn(Future.successful(Some(Box(boxId = BoxId(UUID.randomUUID()), boxName = boxName, BoxCreator(clientId)))))

        val result = doGet(s"/box?boxName=$boxName", validHeaders)
        status(result) should be(BAD_REQUEST)
        Helpers.contentAsString(result) shouldBe "{\"code\":\"BAD_REQUEST\",\"message\":\"Must specify both boxName and clientId query parameters or neither\"}"
        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return NOTFOUND when requested box does not exist" in {
        when(BoxServiceMock.aMock.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))).thenReturn(Future.successful(None))

        val result = doGet(s"/box?boxName=$boxName&clientId=${clientId.value}", validHeaders)

        status(result) should be(NOT_FOUND)
        Helpers.contentAsString(result) shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"

        verify(BoxServiceMock.aMock).getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))
      }
    }

    "getBoxesByClientId" should {

      "return empty list when client has no boxes" in {
        primeAuthAction(clientId.value)

        when(BoxServiceMock.aMock.getBoxesByClientId(eqTo(clientId))).thenReturn(Future.successful(List()))
        val result = doGet(s"/cmb/box", validHeaders)

        contentAsString(result) shouldBe "[]"

        verify(BoxServiceMock.aMock).getBoxesByClientId(eqTo(clientId))
      }

      "return boxes for client specified in auth token in json format" in {
        primeAuthAction(clientId.value)

        when(BoxServiceMock.aMock.getBoxesByClientId(eqTo(clientId))).thenReturn(Future.successful(List(box)))
        val result = doGet("/cmb/box", validHeaders)
        val expected =
          s"""[{"boxId":"${boxId.value.toString}","boxName":"DEFAULT","boxCreator":{"clientId":"$clientId.value"},"clientManaged":false}]"""

        contentAsString(result) shouldBe expected

        verify(BoxServiceMock.aMock).getBoxesByClientId(eqTo(clientId))
      }

      "return a 500 response code if service fails with an exception" in {
        primeAuthAction(clientId.value)

        when(BoxServiceMock.aMock.getBoxesByClientId(eqTo(clientId))).thenReturn(Future.failed(new RuntimeException("some error")))

        val result = doGet("/cmb/box", validHeaders)

        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(BoxServiceMock.aMock).getBoxesByClientId(eqTo(clientId))
      }

      // All these test cases below should probably be replaced by an assertion that the correct ActionFilters have
      // been called.
      "return unauthorised if bearer token doesn't contain client ID" in {
        when(mockAuthConnector.authorise[Option[String]](*, *)(*, *)).thenReturn(Future.successful(None))

        val result = doGet("/cmb/box", validHeaders)

        status(result) should be(UNAUTHORIZED)
      }

      "return 406 when accept header is missing" in {
        primeAuthAction(clientId.value)

        val result = doGet("/cmb/box", validHeaders - ACCEPT)

        status(result) shouldBe NOT_ACCEPTABLE
        (contentAsJson(result) \ "code").as[String] shouldBe "ACCEPT_HEADER_INVALID"
        (contentAsJson(result) \ "message").as[String] shouldBe "The accept header is missing or invalid"
      }

      "return 406 when accept header is invalid" in {
        primeAuthAction(clientId.value)

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
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackUrlUpdated()))

        val result =
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe """{"successful":true}"""
      }

      "return 200 when request contains " in {
        setUpAppConfig(List("api-subscription-fields"))
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackUrlUpdated()))

        val result =
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe """{"successful":true}"""
      }

      "return 401 if User-Agent is not allowlisted" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackUrlUpdated()))

        val result =
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeaders,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(FORBIDDEN)
        Helpers.contentAsString(result) shouldBe """{"code":"FORBIDDEN","message":"Authorisation failed"}"""
      }

      "return 404 if Box does not exist" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(BoxIdNotFound()))

        val result =
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(NOT_FOUND)
      }

      "return 200, successful false and errormessage when mongo update fails" in {
        setUpAppConfig(List("api-subscription-fields"))
        val errorMessage = "Unable to update"
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(UnableToUpdateCallbackUrl(errorMessage)))

        val result =
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe s"""{"successful":false,"errorMessage":"$errorMessage"}"""
      }

      "return 200, successful false and errormessage when callback validation fails" in {
        setUpAppConfig(List("api-subscription-fields"))
        val errorMessage = "Unable to update"
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackValidationFailed(errorMessage)))

        val result =
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe s"""{"successful":false,"errorMessage":"$errorMessage"}"""
      }

      "return 401 if client id does not match that on the box" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(UpdateCallbackUrlUnauthorisedResult()))

        val result =
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          )

        status(result) should be(UNAUTHORIZED)
      }

      "return 400 when payload is non JSON" in {
        val result = doPut(s"/box/5fc1f8e5-8881-4863-8a8c-5c897bb56815/callback", validHeadersWithValidUserAgent, "someBody")

        status(result) should be(BAD_REQUEST)
        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 400 when payload is missing the clientId value" in {
        setUpAppConfig(List("api-subscription-fields"))
        val result =
          doPut(s"/box/5fc1f8e5-8881-4863-8a8c-5c897bb56815/callback", validHeadersWithValidUserAgent, createRequest("", "callbackUrl"))

        status(result) should be(BAD_REQUEST)
        Helpers.contentAsString(result) shouldBe """{"code":"INVALID_REQUEST_PAYLOAD","message":"clientId is required"}"""
        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 200 when payload is missing the callbackUrl value" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackUrlUpdated()))
        val result = doPut(s"/box/${boxId.value.toString}/callback", validHeadersWithValidUserAgent, createRequest(clientId.value, ""))

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe """{"successful":true}"""
        verify(BoxServiceMock.aMock).updateCallbackUrl(eqTo(boxId), *, *)(*, *)
      }
    }

    "updatedManagedBoxCallbackUrl" should {
      def createRequest(callBackUrl: String): String = s"""{"callbackUrl": "$callBackUrl"}"""

      "return 200 when request is successful" in {
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackUrlUpdated()))
        primeAuthAction(clientId.value)

        val result = doPut(
          s"/cmb/box/${boxId.value.toString}/callback",
          validHeadersJson,
          createRequest("callbackUrl")
        )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe """{"successful":true}"""
      }

      "return 404 if Box does not exist" in {
        primeAuthAction(clientId.value)
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(BoxIdNotFound()))

        val result = doPut(
          s"/cmb/box/${boxId.value.toString}/callback",
          validHeadersJson,
          createRequest("callbackUrl")
        )

        status(result) should be(NOT_FOUND)
      }

      "return 200, successful false and errormessage when mongo update fails" in {
        primeAuthAction(clientId.value)
        val errorMessage = "Unable to update"
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(UnableToUpdateCallbackUrl(errorMessage)))

        val result =
          doPut(
            s"/cmb/box/${boxId.value.toString}/callback",
            validHeadersJson,
            createRequest("callbackUrl")
          )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe s"""{"successful":false,"errorMessage":"$errorMessage"}"""
      }

      "return 200, successful false and errormessage when callback validation fails" in {
        primeAuthAction(clientId.value)
        val errorMessage = "Unable to update"
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackValidationFailed(errorMessage)))

        val result = doPut(
          s"/cmb/box/${boxId.value.toString}/callback",
          validHeadersJson,
          createRequest("callbackUrl")
        )

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe s"""{"successful":false,"errorMessage":"$errorMessage"}"""
      }

      "return 403 if client id does not match that on the box" in {
        primeAuthAction(clientId.value)
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(UpdateCallbackUrlUnauthorisedResult()))

        val result = doPut(
          s"/cmb/box/${boxId.value.toString}/callback",
          validHeadersJson,
          createRequest("callbackUrl")
        )

        status(result) should be(FORBIDDEN)
      }

      "return 400 when payload is non JSON" in {
        val result = doPut(s"/cmb/box/5fc1f8e5-8881-4863-8a8c-5c897bb56815/callback", validHeadersJson, "someBody")

        status(result) should be(BAD_REQUEST)
        verifyNoInteractions(BoxServiceMock.aMock)
      }

      "return 200 when payload is missing the callbackUrl value" in {
        primeAuthAction(clientId.value)
        when(BoxServiceMock.aMock.updateCallbackUrl(eqTo(boxId), *, *)(*, *))
          .thenReturn(Future.successful(CallbackUrlUpdated()))
        val result = doPut(s"/cmb/box/${boxId.value.toString}/callback", validHeadersJson, createRequest(""))

        status(result) should be(OK)
        Helpers.contentAsString(result) shouldBe """{"successful":true}"""
        verify(BoxServiceMock.aMock).updateCallbackUrl(eqTo(boxId), *, *)(*, *)
      }
    }

    "validateBoxOwnership" should {
      def validateBody(boxIdStr: String, clientId: String): String = s"""{"boxId":"$boxIdStr","clientId":"$clientId"}"""

      "return 200 and valid when ownership confirmed" in {
        when(BoxServiceMock.aMock.validateBoxOwner(eqTo(boxId), eqTo(clientId))(*))
          .thenReturn(Future.successful(ValidateBoxOwnerSuccessResult()))

        val result = doPost("/cmb/validate", validHeaders, validateBody(boxId.value.toString, clientId.value))

        status(result) shouldBe OK
        (contentAsJson(result) \ "valid").as[Boolean] shouldBe true
      }

      "return 200 and invalid when ownership not confirmed" in {
        when(BoxServiceMock.aMock.validateBoxOwner(eqTo(boxId), eqTo(clientId))(*))
          .thenReturn(Future.successful(ValidateBoxOwnerFailedResult("Ownership doesn't match")))

        val result = doPost("/cmb/validate", validHeaders, validateBody(boxId.value.toString, clientId.value))

        status(result) shouldBe OK
        (contentAsJson(result) \ "valid").as[Boolean] shouldBe false
      }

      "return 404 when box not found" in {
        when(BoxServiceMock.aMock.validateBoxOwner(eqTo(boxId), eqTo(clientId))(*))
          .thenReturn(Future.successful(ValidateBoxOwnerNotFoundResult("Box not found")))

        val result = doPost("/cmb/validate", validHeaders, validateBody(boxId.value.toString, clientId.value))

        status(result) shouldBe NOT_FOUND
        (contentAsJson(result) \ "code").as[String] shouldBe "BOX_NOT_FOUND"
        (contentAsJson(result) \ "message").as[String] shouldBe "Box not found"
      }

      "return 400 when clientId is empty" in {
        val result = doPost("/cmb/validate", validHeaders, validateBody(boxId.value.toString, ""))

        status(result) shouldBe BAD_REQUEST
        (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_REQUEST_PAYLOAD"
        (contentAsJson(result) \ "message").as[String] shouldBe "Expecting boxId and clientId in request body"
      }

      "return 400 when format is wrong" in {
        val result = doPost("/cmb/validate", validHeaders, s"""{"boxId":"$boxId.value.toString"}""")

        status(result) shouldBe BAD_REQUEST
        (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_REQUEST_PAYLOAD"
        (contentAsJson(result) \ "message").as[String] shouldBe "JSON body is invalid against expected format"
      }

      "return 406 when accept header is missing" in {
        val result = doPost("/cmb/validate", validHeaders - ACCEPT, validateBody(boxId.value.toString, clientId.value))

        status(result) shouldBe NOT_ACCEPTABLE
        (contentAsJson(result) \ "code").as[String] shouldBe "ACCEPT_HEADER_INVALID"
        (contentAsJson(result) \ "message").as[String] shouldBe "The accept header is missing or invalid"
      }

      "return 406 when accept header is invalid" in {
        val result = doPost("/cmb/validate", validHeaders + (ACCEPT -> "XYZAccept"), validateBody(boxId.value.toString, clientId.value))

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

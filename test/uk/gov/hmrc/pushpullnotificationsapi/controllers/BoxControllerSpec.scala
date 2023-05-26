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
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_REQUEST, route, status, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.mocks.BoxServiceMockModule
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters.boxFormats
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.services.BoxService
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

import java.util.UUID
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class BoxControllerSpec extends AsyncHmrcSpec with BoxServiceMockModule with TestData with GuiceOneAppPerSuite with BeforeAndAfterEach {

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


  "BoxController" when {
    def validateResult(result: Future[Result], expectedStatus: Int, expectedBodyStr: String) = {
      status(result) should be(expectedStatus)

      expectedStatus match {
        case INTERNAL_SERVER_ERROR => succeed
        case NO_CONTENT            => succeed
        case _                     => contentAsJson(result) should be(Json.parse(expectedBodyStr))
      }
    }

    "createBox" should {

      "return 201 and boxId when box successfully created" in {
        setUpAppConfig(List("api-subscription-fields"))
        BoxServiceMock.CreateBox.thenSucceedCreated(box)

        validateResult(doPut("/box", validHeadersWithValidUserAgent, jsonBody), CREATED, s"""{"boxId":"${boxId.value.toString}"}""")

        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName, isClientManaged = false)
      }

      "return 200 and boxId when box already exists" in {
        setUpAppConfig(List("api-subscription-fields"))

        BoxServiceMock.CreateBox.thenSucceedRetrieved(box)

        validateResult(doPut("/box", validHeadersWithValidUserAgent, jsonBody), OK, s"""{"boxId":"${boxId.value.toString}"}""")

        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName, isClientManaged = false)
      }

      "return 400 when payload is completely invalid against expected format" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(
          doPut("/box", validHeadersWithValidUserAgent, "{\"someOtherJson\":\"value\"}"),
          BAD_REQUEST,
          s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 400 when request payload is missing boxName" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(
          doPut("/box", validHeadersWithValidUserAgent, emptyJsonBody(boxNameVal = "")),
          BAD_REQUEST,
          s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"Expecting boxName and clientId in request body"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 415 when content type header is invalid" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(
          doPut("/box", validHeadersWithInValidContentType, jsonBody),
          UNSUPPORTED_MEDIA_TYPE,
          s"""{"code":"BAD_REQUEST","message":"Expecting text/json or application/json body"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 415 when content type header is empty" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(
          doPut("/box", validHeadersWithEmptyContentType, jsonBody),
          UNSUPPORTED_MEDIA_TYPE,
          s"""{"code":"BAD_REQUEST","message":"Expecting text/json or application/json body"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 422 when Left returned from Box Service" in {
        setUpAppConfig(List("api-subscription-fields"))
        BoxServiceMock.CreateBox.thenFailsWithBoxName(boxName, clientId)

        validateResult(
          doPut("/box", validHeadersWithValidUserAgent, jsonBody),
          UNPROCESSABLE_ENTITY,
          s"""{"code":"UNKNOWN_ERROR","message":"unable to createBox:Box with name :$boxName already exists for clientId: ${clientId.value} but unable to retrieve" }"""
        )

        verify(BoxServiceMock.aMock).createBox(eqTo(clientId), eqTo(boxName), eqTo(false))(*, *)
      }

      "return 400 when useragent config is empty" in {
        setUpAppConfig(List.empty)

        validateResult(doPut("/box", validHeadersWithValidUserAgent, jsonBody), INTERNAL_SERVER_ERROR, s"""{}""")

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 403 when invalid useragent provided" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(doPut("/box", validHeadersWithInValidUserAgent, jsonBody), FORBIDDEN, s"""{"code":"FORBIDDEN","message":"Authorisation failed"}""")

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 403 when no useragent header provided" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(doPut("/box", validHeaders, jsonBody), FORBIDDEN, s"""{"code":"FORBIDDEN","message":"Authorisation failed"}""")

        BoxServiceMock.verifyZeroInteractions()
      }

      "return 500 when service fails with any runtime exception" in {
        setUpAppConfig(List("api-subscription-fields"))

        BoxServiceMock.CreateBox.thenFailsWithException("some error")

        validateResult(doPut("/box", validHeadersWithValidUserAgent, jsonBody), INTERNAL_SERVER_ERROR, "")

        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName, false)
      }

      "return 400 when non JSon payload sent" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(
          doPut("/box", validHeadersWithValidUserAgent, "xxx"),
          BAD_REQUEST,
          """{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 400 when invalid JSon payload sent" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(
          doPut("/box", validHeadersWithValidUserAgent, "{}"),
          BAD_REQUEST,
          """{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }
    }

    "createClientManagedBox" should {

      "return unauthorised if bearer token doesn't contain client ID" in {
        when(mockAuthConnector.authorise[Option[String]](*, *)(*, *)).thenReturn(Future.successful(None))

        validateResult(
          doPut("/cmb/box", validHeadersJson, jsonBody),
          expectedStatus = UNAUTHORIZED,
          expectedBodyStr = s"""{"code":"UNAUTHORISED","message":"Unable to retrieve ClientId"}"""
        )

      }

      "return 201 and boxId when box successfully created" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.CreateBox.thenSucceedCreated(box)

        validateResult(doPut("/cmb/box", validHeadersJson, jsonBody), expectedStatus = CREATED, expectedBodyStr = s"""{"boxId":"${boxId.value.toString}"}""")

        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName, true)
      }

      "return 200 and boxId when box already exists" in {
        primeAuthAction(clientId.value)
        BoxServiceMock.CreateBox.thenSucceedRetrieved(box)

        validateResult(doPut("/cmb/box", validHeadersJson, jsonBody), expectedStatus = OK, expectedBodyStr = s"""{"boxId":"${boxId.value.toString}"}""")

        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName, true)
      }

      "return 400 when payload is completely invalid against expected format" in {
        primeAuthAction(clientId.value)

        validateResult(
          doPut("/cmb/box", validHeadersJson, "{\"someOtherJson\":\"value\"}"),
          expectedStatus = BAD_REQUEST,
          expectedBodyStr = s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 400 when request payload is missing boxName" in {
        primeAuthAction(clientId.value)

        validateResult(
          doPut("/cmb/box", validHeadersJson, s"""{"boxName":""}"""),
          expectedStatus = BAD_REQUEST,
          expectedBodyStr = s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"Expecting boxName in request body"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 406 when accept header is invalid" in {
        primeAuthAction(clientId.value)

        validateResult(
          doPut("/cmb/box", validHeadersWithInvalidAcceptHeader, jsonBody),
          expectedStatus = NOT_ACCEPTABLE,
          expectedBodyStr = s"""{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 415 when content type header is invalid" in {
        primeAuthAction(clientId.value)

        validateResult(
          doPut("/cmb/box", validHeadersWithInValidContentType, jsonBody),
          expectedStatus = UNSUPPORTED_MEDIA_TYPE,
          expectedBodyStr = s"""{"code":"BAD_REQUEST","message":"Expecting text/json or application/json body"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 415 when content type header is empty" in {
        primeAuthAction(clientId.value)

        validateResult(
          doPut("/cmb/box", validHeadersWithEmptyContentType, jsonBody),
          expectedStatus = UNSUPPORTED_MEDIA_TYPE,
          expectedBodyStr = s"""{"code":"BAD_REQUEST","message":"Expecting text/json or application/json body"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 422 when Left returned from Box Service" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.CreateBox.thenFailsWithCreateFailedResult("some error")

        validateResult(
          doPut("/cmb/box", validHeadersJson, jsonBody),
          expectedStatus = UNPROCESSABLE_ENTITY,
          expectedBodyStr = s"""{"code":"UNKNOWN_ERROR","message":"unable to createBox:some error"}"""
        )


        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName, true)
      }

      "return 500 when service fails with any runtime exception" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.CreateBox.thenFailsWithException("some error")

        validateResult(doPut("/cmb/box", validHeadersJson, jsonBody), expectedStatus = INTERNAL_SERVER_ERROR, expectedBodyStr = "")

        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName, true)
      }

      "return 400 when non JSon payload sent" in {
        primeAuthAction(clientId.value)

        validateResult(
          doPut("/cmb/box", validHeadersJson, "xxx"),
          expectedStatus = BAD_REQUEST,
          expectedBodyStr = """{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }

      "return 400 when invalid JSon payload sent" in {
        primeAuthAction(clientId.value)

        validateResult(
          doPut("/cmb/box", validHeadersJson, "{}"),
          expectedStatus = BAD_REQUEST,
          expectedBodyStr = """{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}"""
        )

         BoxServiceMock.verifyZeroInteractions()
      }
    }

    "deleteClientManagedBox" should {

      "return unauthorised if bearer token doesn't contain client ID on a box delete" in {
        when(mockAuthConnector.authorise[Option[String]](*, *)(*, *)).thenReturn(Future.successful(None))

        validateResult(
          doDelete(s"/cmb/box/${boxId.value.toString}", validHeadersWithAcceptHeader),
          expectedStatus = UNAUTHORIZED,
          expectedBodyStr = """{"code":"UNAUTHORISED","message":"Unable to retrieve ClientId"}"""
        )

        BoxServiceMock.verifyZeroInteractions()
      }

      "return 201 and boxId when box successfully deleted" in {
        primeAuthAction(clientId.value)
        BoxServiceMock.DeleteBox.isSuccessful()

        validateResult(doDelete(s"/cmb/box/${boxId.value.toString}", validHeadersWithAcceptHeader), expectedStatus = NO_CONTENT, expectedBodyStr = "")

        BoxServiceMock.DeleteBox.verifyCalledWith(clientId, boxId)
      }

      "return 404(NOT FOUND) when attempting to delete a box with an ID that does not exist" in {
        primeAuthAction(clientId.value)
        BoxServiceMock.DeleteBox.failsNotFound()

        validateResult(
          doDelete(s"/cmb/box/${boxId.value.toString}", validHeadersWithAcceptHeader),
          expectedStatus = NOT_FOUND,
          expectedBodyStr = """{"code":"BOX_NOT_FOUND","message":"Box not found"}"""
        )

        BoxServiceMock.DeleteBox.verifyCalledWith(clientId, boxId)
      }

      "return 403(FORBIDDEN) when Attempt to delete a box which for a different client ID" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.DeleteBox.failsAccessDenied()

        validateResult(
          doDelete(s"/cmb/box/${boxId.value.toString}", validHeadersWithAcceptHeader),
          expectedStatus = FORBIDDEN,
          expectedBodyStr = """{"code":"FORBIDDEN","message":"Access denied"}"""
        )

        BoxServiceMock.DeleteBox.verifyCalledWith(clientId, boxId)
      }

      "return 406 when a Incorrect Accept Header Version" in {
        primeAuthAction(clientId.value)

        validateResult(
          doDelete(s"/cmb/box/${boxId.value.toString}", validHeadersWithInvalidAcceptHeader.toList),
          expectedStatus = NOT_ACCEPTABLE,
          expectedBodyStr = """{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}"""
        )

        BoxServiceMock.verifyZeroInteractions()
      }

      "return 422 when Left returned from Box Service" in {
        primeAuthAction(clientId.value)
        val errorMsg = s"Box with name :$boxName and clientId: ${clientId.value} but unable to delete"
        BoxServiceMock.DeleteBox.failedResultWithText(errorMsg)

        validateResult(
          doDelete(s"/cmb/box/${boxId.value.toString}", validHeadersWithAcceptHeader),
          expectedStatus = UNPROCESSABLE_ENTITY,
          expectedBodyStr = s"""{"code":"UNKNOWN_ERROR","message":"unable to deleteBox:$errorMsg"}"""
        )

        BoxServiceMock.DeleteBox.verifyCalledWith(clientId, boxId)
      }

      "return 500 when service fails with any runtime exception" in {
        primeAuthAction(clientId.value)
        BoxServiceMock.DeleteBox.failsWithException("some error")

        validateResult(
          doDelete(s"/cmb/box/${boxId.value.toString}", validHeadersWithAcceptHeader),
          expectedStatus = INTERNAL_SERVER_ERROR,
          expectedBodyStr = s"""{"code":"UNKNOWN_ERROR","message":"blah"}"""
        )

        BoxServiceMock.DeleteBox.verifyCalledWith(clientId, boxId)
      }
    }

    "getBoxes" should {

      "return 200 but call getAllBoxes when no parameters provided" in {
        val expectedBoxes = List(Box(boxId = BoxId(UUID.randomUUID()), boxName = boxName, BoxCreator(clientId)))

        BoxServiceMock.GetAllBoxes.thenSuccess(expectedBoxes)

        validateResult(doGet(s"/box", validHeaders), OK, Json.toJson(expectedBoxes).toString())

        BoxServiceMock.GetAllBoxes.verifyCalled
      }

      "return 500 when  getAllBoxes fails when no parameters provided" in {

        BoxServiceMock.GetAllBoxes.fails("some error")

        validateResult(doGet(s"/box", validHeaders), INTERNAL_SERVER_ERROR, "")

        BoxServiceMock.GetAllBoxes.verifyCalled
      }

      "return 200 and requested box when it exists" in {
        BoxServiceMock.GetBoxByNameAndClientId.thenSuccess(Some(Box(boxId, boxName, BoxCreator(clientId))))

        validateResult(doGet(s"/box?boxName=$boxName&clientId=${clientId.value}", validHeaders), OK, Json.toJson(box).toString())

        BoxServiceMock.GetBoxByNameAndClientId.verifyCalledWith(boxName, clientId)
      }

      "return 400 when boxName is missing" in {

        validateResult(
          doGet(s"/box?clientId=${clientId.value}", validHeaders),
          BAD_REQUEST,
          """{"code":"BAD_REQUEST","message":"Must specify both boxName and clientId query parameters or neither"}"""
        )

        BoxServiceMock.GetBoxByNameAndClientId.verifyNoInteractions()
      }

      "return 400 when clientId is missing" in {

        validateResult(
          doGet(s"/box?boxName=$boxName", validHeaders),
          BAD_REQUEST,
          """{"code":"BAD_REQUEST","message":"Must specify both boxName and clientId query parameters or neither"}"""
        )

        BoxServiceMock.GetBoxByNameAndClientId.verifyNoInteractions()
      }

      "return NOTFOUND when requested box does not exist" in {

        BoxServiceMock.GetBoxByNameAndClientId.thenSuccess(None)

        validateResult(doGet(s"/box?boxName=$boxName&clientId=${clientId.value}", validHeaders), NOT_FOUND, """{"code":"BOX_NOT_FOUND","message":"Box not found"}""")

        BoxServiceMock.GetBoxByNameAndClientId.verifyCalledWith(boxName, clientId)
      }
    }

    "getBoxesByClientId" should {

      "return empty list when client has no boxes" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.GetBoxesByClientId.thenSuccessWith(clientId, List.empty[Box])

        validateResult(doGet(s"/cmb/box", validHeaders), OK, "[]")

        BoxServiceMock.GetBoxesByClientId.verifyCalledWith(clientId)
      }

      "return boxes for client specified in auth token in json format" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.GetBoxesByClientId.thenSuccessWith(clientId, List(box))

        validateResult(doGet(s"/cmb/box", validHeaders), OK, Json.toJson(List(box.copy(boxName = "DEFAULT"))).toString())

        BoxServiceMock.GetBoxesByClientId.verifyCalledWith(clientId)
      }

      "return a 500 response code if service fails with an exception" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.GetBoxesByClientId.theFailsWith(clientId, new RuntimeException("some error"))

        validateResult(doGet(s"/cmb/box", validHeaders), INTERNAL_SERVER_ERROR, "")

        BoxServiceMock.GetBoxesByClientId.verifyCalledWith(clientId)
      }

      // All these test cases below should probably be replaced by an assertion that the correct ActionFilters have
      // been called.
      "return unauthorised if bearer token doesn't contain client ID" in {
        when(mockAuthConnector.authorise[Option[String]](*, *)(*, *)).thenReturn(Future.successful(None))

        validateResult(doGet(s"/cmb/box", validHeaders), UNAUTHORIZED, """{"code":"UNAUTHORISED","message":"Unable to retrieve ClientId"}""")

        BoxServiceMock.GetBoxesByClientId.verifyNoInteractions()
      }

      "return 406 when accept header is missing" in {
        primeAuthAction(clientId.value)

        validateResult(doGet(s"/cmb/box", validHeaders - ACCEPT), NOT_ACCEPTABLE, """{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}""")

      }

      "return 406 when accept header is invalid" in {
        primeAuthAction(clientId.value)

        validateResult(
          doGet(s"/cmb/box", validHeaders + (ACCEPT -> "XYZAccept")),
          NOT_ACCEPTABLE,
          """{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}"""
        )
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

      val isClientManaged = false

      "return 200 when request is successful" in {
        setUpAppConfig(List("api-subscription-fields"))
        BoxServiceMock.UpdateCallbackUrl.thenSucceedsWith(boxId, isClientManaged, CallbackUrlUpdated())

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")),
          OK,
          """{"successful":true}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId, isClientManaged)
      }

      "return 200 when payload is missing the callbackUrl value" in {
        setUpAppConfig(List("api-subscription-fields"))

        BoxServiceMock.UpdateCallbackUrl.thenSucceedsWith(boxId, isClientManaged, CallbackUrlUpdated())

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "")),
          OK,
          """{"successful":true}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId, isClientManaged)
      }

      "return 401 if User-Agent is not allowlisted" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeaders,
            createRequest("clientId", "callbackUrl")),
          FORBIDDEN,
          """{"code":"FORBIDDEN","message":"Authorisation failed"}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyNoInteractions()
      }

      "return 404 if Box does not exist" in {
        setUpAppConfig(List("api-subscription-fields"))

        BoxServiceMock.UpdateCallbackUrl.failsWith(boxId, isClientManaged, BoxIdNotFound())

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")),
          NOT_FOUND,
          """{"code":"BOX_NOT_FOUND","message":"Box not found"}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId)
      }

      "return 200, successful false and errormessage when mongo update fails" in {
        setUpAppConfig(List("api-subscription-fields"))
        val errorMessage = "Unable to update"
        BoxServiceMock.UpdateCallbackUrl.failsWith(boxId, isClientManaged, UnableToUpdateCallbackUrl(errorMessage))

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")),
          OK,
          s"""{"successful":false,"errorMessage":"$errorMessage"}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId, isClientManaged)
      }

      "return 200, successful false and errormessage when callback validation fails" in {
        setUpAppConfig(List("api-subscription-fields"))
        val errorMessage = "Unable to update"

        BoxServiceMock.UpdateCallbackUrl.failsWith(boxId, isClientManaged,  CallbackValidationFailed(errorMessage))

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")),
          OK,
          s"""{"successful":false,"errorMessage":"$errorMessage"}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId, isClientManaged)
      }

      "return 401 if client id does not match that on the box" in {
        setUpAppConfig(List("api-subscription-fields"))

        BoxServiceMock.UpdateCallbackUrl.failsWith(boxId, isClientManaged,  UpdateCallbackUrlUnauthorisedResult())

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")),
          UNAUTHORIZED,
          s"""{"code":"UNAUTHORISED","message":"Client Id did not match"}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId, isClientManaged)
      }

      "return 400 when payload is non JSON" in {
        validateResult(
          doPut(s"/box/${boxId.value.toString}/callback", validHeadersWithValidUserAgent, "someBody"),
          BAD_REQUEST,
          s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyNoInteractions()
      }

      "return 400 when payload is missing the clientId value" in {
        setUpAppConfig(List("api-subscription-fields"))


        validateResult(
            doPut(s"/box/${boxId.value.toString}/callback", validHeadersWithValidUserAgent, createRequest("", "callbackUrl")),
            BAD_REQUEST,
        s"""{"code":"INVALID_REQUEST_PAYLOAD","message":"clientId is required"}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyNoInteractions()
      }

    }

    "updatedManagedBoxCallbackUrl" should {
      def createRequest(callBackUrl: String): String = s"""{"callbackUrl": "$callBackUrl"}"""

      val isClientManaged = true

      "return 200 when request is successful" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.UpdateCallbackUrl.thenSucceedsWith(boxId, isClientManaged, CallbackUrlUpdated())

        validateResult(doPut(
        s"/cmb/box/${boxId.value.toString}/callback",
            validHeadersJson,
            createRequest("callbackUrl")
        ),
            OK,
        """{"successful":true}""")

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId, isClientManaged)
      }

      "return 200 when payload is missing the callbackUrl value" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.UpdateCallbackUrl.thenSucceedsWith(boxId, isClientManaged, CallbackUrlUpdated())

        validateResult(doPut(
          s"/cmb/box/${boxId.value.toString}/callback",
          validHeadersJson,
          createRequest("")),
          OK,"""{"successful":true}""")

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId, isClientManaged)
      }

      "return 404 if Box does not exist" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.UpdateCallbackUrl.failsWith(boxId, isClientManaged, BoxIdNotFound())

        validateResult(doPut(
        s"/cmb/box/${boxId.value.toString}/callback",
            validHeadersJson,
            createRequest("callbackUrl")
        ),
            NOT_FOUND,
        """{"code":"BOX_NOT_FOUND","message":"Box not found"}""")

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId, isClientManaged)
      }

      "return 200, successful false and errormessage when mongo update fails" in {
        primeAuthAction(clientId.value)
        val errorMessage = "Unable to update"

        BoxServiceMock.UpdateCallbackUrl.failsWith(boxId, isClientManaged, UnableToUpdateCallbackUrl(errorMessage))

        validateResult(doPut(
        s"/cmb/box/${boxId.value.toString}/callback",
            validHeadersJson,
            createRequest("callbackUrl")
        ),
            OK,
        s"""{"successful":false,"errorMessage":"$errorMessage"}""")

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId, isClientManaged)
      }

      "return 200, successful false and errormessage when callback validation fails" in {
        primeAuthAction(clientId.value)
        val errorMessage = "Unable to update"

        BoxServiceMock.UpdateCallbackUrl.failsWith(boxId, isClientManaged, UnableToUpdateCallbackUrl(errorMessage))

        validateResult(doPut(
        s"/cmb/box/${boxId.value.toString}/callback",
            validHeadersJson,
            createRequest("callbackUrl")
        ),
            OK,
        s"""{"successful":false,"errorMessage":"$errorMessage"}""")

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId, isClientManaged)
      }

      "return 403 if client id does not match that on the box" in {
        primeAuthAction(clientId.value)

        BoxServiceMock.UpdateCallbackUrl.failsWith(boxId, isClientManaged, UpdateCallbackUrlUnauthorisedResult())

        validateResult(doPut(
        s"/cmb/box/${boxId.value.toString}/callback",
            validHeadersJson,
            createRequest("callbackUrl")
        ),
            FORBIDDEN,
        """{"code":"FORBIDDEN","message":"Access denied"}""")

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId, isClientManaged)
      }

      "return 400 when payload is non JSON" in {
        validateResult(doPut(
        s"/cmb/box/${boxId.value.toString}/callback",
            validHeadersJson,
        "someBody"),
            BAD_REQUEST,"""{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}""")

        BoxServiceMock.UpdateCallbackUrl.verifyNoInteractions()
      }
    }

    "validateBoxOwnership" should {
      def validateBody(boxIdStr: String, clientId: String): String = s"""{"boxId":"$boxIdStr","clientId":"$clientId"}"""

      "return 200 and valid when ownership confirmed" in {
        BoxServiceMock.ValidateBoxOwner.thenSucceedsWith(boxId, clientId)

        validateResult(doPost("/cmb/validate", validHeaders, validateBody(boxId.value.toString, clientId.value)),
          OK, """{"valid":true}""")

        BoxServiceMock.ValidateBoxOwner.verifyCalledWith(boxId, clientId)
      }

      "return 200 and invalid when ownership not confirmed" in {
        BoxServiceMock.ValidateBoxOwner.thenFailsWith(boxId, clientId, ValidateBoxOwnerFailedResult("Ownership doesn't match"))

        validateResult(doPost("/cmb/validate", validHeaders, validateBody(boxId.value.toString, clientId.value)),
          OK, """{"valid":false}""")

        BoxServiceMock.ValidateBoxOwner.verifyCalledWith(boxId, clientId)
      }

      "return 404 when box not found" in {
        BoxServiceMock.ValidateBoxOwner.thenFailsWith(boxId, clientId, ValidateBoxOwnerNotFoundResult("Box not found"))

        validateResult(doPost("/cmb/validate", validHeaders, validateBody(boxId.value.toString, clientId.value)),
          NOT_FOUND, """{"code":"BOX_NOT_FOUND","message":"Box not found"}""")

        BoxServiceMock.ValidateBoxOwner.verifyCalledWith(boxId, clientId)
      }

      "return 400 when clientId is empty" in {
        validateResult(doPost("/cmb/validate", validHeaders, validateBody(boxId.value.toString, "")),
          BAD_REQUEST, """{"code":"INVALID_REQUEST_PAYLOAD","message":"Expecting boxId and clientId in request body"}""")

        BoxServiceMock.ValidateBoxOwner.verifyNoInteractions()
      }

      "return 400 when format is wrong" in {
        validateResult(doPost("/cmb/validate", validHeaders, s"""{"boxId":"${boxId.value.toString}"}"""),
          BAD_REQUEST, """{"code":"INVALID_REQUEST_PAYLOAD","message":"JSON body is invalid against expected format"}""")

        BoxServiceMock.ValidateBoxOwner.verifyNoInteractions()

      }

      "return 406 when accept header is missing" in {

        validateResult(doPost("/cmb/validate", validHeaders - ACCEPT, validateBody(boxId.value.toString, clientId.value)),
          NOT_ACCEPTABLE, """{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}""")

        BoxServiceMock.ValidateBoxOwner.verifyNoInteractions()

      }

      "return 406 when accept header is invalid" in {
        validateResult(doPost("/cmb/validate", validHeaders + (ACCEPT -> "XYZAccept"), validateBody(boxId.value.toString, clientId.value)),
          NOT_ACCEPTABLE, """{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}""")

        BoxServiceMock.ValidateBoxOwner.verifyNoInteractions()
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

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

import java.util.UUID
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import org.apache.pekko.stream.Materializer
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
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
import uk.gov.hmrc.pushpullnotificationsapi.mocks.connectors.AuthConnectorMockModule
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters.boxFormats
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.services.BoxService
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

class BoxControllerSpec extends AsyncHmrcSpec with BoxServiceMockModule with AuthConnectorMockModule with TestData with GuiceOneAppPerSuite with BeforeAndAfterEach {

  implicit def mat: Materializer = app.injector.instanceOf[Materializer]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockAppConfig: AppConfig = mock[AppConfig]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[BoxService].to(BoxServiceMock.aMock))
    .overrides(bind[AppConfig].to(mockAppConfig))
    .overrides(bind[AuthConnector].to(AuthConnectorMock.aMock))
    .build()

  override def beforeEach(): Unit = {
    reset(BoxServiceMock.aMock, mockAppConfig, AuthConnectorMock.aMock)
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
        BoxServiceMock.CreateBox.thenSucceedCreated(BoxObjectWithNoSubscribers)

        validateResult(doPut("/box", validHeadersWithValidUserAgent, jsonBody), CREATED, s"""{"boxId":"${boxId.value.toString}"}""")

        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName)
      }

      "return 200 and boxId when box already exists" in {
        setUpAppConfig(List("api-subscription-fields"))

        BoxServiceMock.CreateBox.thenSucceedRetrieved(BoxObjectWithNoSubscribers)

        validateResult(doPut("/box", validHeadersWithValidUserAgent, jsonBody), OK, s"""{"boxId":"${boxId.value.toString}"}""")

        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName)
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

        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName)
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

        BoxServiceMock.CreateBox.verifyCalledWith(clientId, boxName)
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

    "getBoxes" should {

      "return 200 but call getAllBoxes when no parameters provided" in {
        val expectedBoxes = List(Box(boxId = BoxId(UUID.randomUUID()), boxName = boxName, BoxCreator(clientId)))

        BoxServiceMock.GetAllBoxes.thenSuccess(expectedBoxes)

        validateResult(doGet(s"/box", validHeaders), OK, Json.toJson(expectedBoxes).toString())

        BoxServiceMock.GetAllBoxes.verifyCalled()
      }

      "return 500 when  getAllBoxes fails when no parameters provided" in {

        BoxServiceMock.GetAllBoxes.fails("some error")

        validateResult(doGet(s"/box", validHeaders), INTERNAL_SERVER_ERROR, "")

        BoxServiceMock.GetAllBoxes.verifyCalled()
      }

      "return 200 and requested box when it exists" in {
        BoxServiceMock.GetBoxByNameAndClientId.thenSuccess(Some(Box(boxId, boxName, BoxCreator(clientId))))

        validateResult(doGet(s"/box?boxName=$boxName&clientId=${clientId.value}", validHeaders), OK, Json.toJson(BoxObjectWithNoSubscribers).toString())

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
        BoxServiceMock.UpdateCallbackUrl.thenSucceedsWith(boxId, CallbackUrlUpdated())

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          ),
          OK,
          """{"successful":true}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId)
      }

      "return 200 when payload is missing the callbackUrl value" in {
        setUpAppConfig(List("api-subscription-fields"))

        BoxServiceMock.UpdateCallbackUrl.thenSucceedsWith(boxId, CallbackUrlUpdated())

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "")
          ),
          OK,
          """{"successful":true}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId)
      }

      "return 401 if User-Agent is not allowlisted" in {
        setUpAppConfig(List("api-subscription-fields"))

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeaders,
            createRequest("clientId", "callbackUrl")
          ),
          FORBIDDEN,
          """{"code":"FORBIDDEN","message":"Authorisation failed"}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyNoInteractions()
      }

      "return 404 if Box does not exist" in {
        setUpAppConfig(List("api-subscription-fields"))

        BoxServiceMock.UpdateCallbackUrl.failsWith(boxId, BoxIdNotFound())

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          ),
          NOT_FOUND,
          """{"code":"BOX_NOT_FOUND","message":"Box not found"}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId)
      }

      "return 200, successful false and errormessage when mongo update fails" in {
        setUpAppConfig(List("api-subscription-fields"))
        val errorMessage = "Unable to update"
        BoxServiceMock.UpdateCallbackUrl.failsWith(boxId, UnableToUpdateCallbackUrl(errorMessage))

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          ),
          OK,
          s"""{"successful":false,"errorMessage":"$errorMessage"}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId)
      }

      "return 200, successful false and errormessage when callback validation fails" in {
        setUpAppConfig(List("api-subscription-fields"))
        val errorMessage = "Unable to update"

        BoxServiceMock.UpdateCallbackUrl.failsWith(boxId, CallbackValidationFailed(errorMessage))

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          ),
          OK,
          s"""{"successful":false,"errorMessage":"$errorMessage"}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId)
      }

      "return 401 if client id does not match that on the box" in {
        setUpAppConfig(List("api-subscription-fields"))

        BoxServiceMock.UpdateCallbackUrl.failsWith(boxId, UpdateCallbackUrlUnauthorisedResult())

        validateResult(
          doPut(
            s"/box/${boxId.value.toString}/callback",
            validHeadersWithValidUserAgent,
            createRequest("clientId", "callbackUrl")
          ),
          UNAUTHORIZED,
          s"""{"code":"UNAUTHORISED","message":"Client Id did not match"}"""
        )

        BoxServiceMock.UpdateCallbackUrl.verifyCalledWith(boxId)
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
  }

  def doGet(uri: String, headers: Map[String, String]): Future[Result] = {
    val fakeRequest = FakeRequest(GET, uri).withHeaders(headers.toSeq: _*)
    route(app, fakeRequest).get
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
}

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

import java.net.URL
import java.nio.charset.Charset
import java.time.{Duration, Instant}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import akka.stream.Materializer
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.auth.core.AuthConnector

import uk.gov.hmrc.apiplatform.modules.common.domain.services.InstantFormatter
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.mocks._
import uk.gov.hmrc.pushpullnotificationsapi.mocks.connectors.AuthConnectorMockModule
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.services.{ConfirmationService, NotificationsService}
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

class WrappedNotificationsControllerSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with BeforeAndAfterEach with ConfirmationServiceMockModule
    with NotificationsServiceMockModule with AuthConnectorMockModule with TestData {

  override lazy val app: Application = GuiceApplicationBuilder()
    .configure(Map("notifications.maxSize" -> "50B"))
    .configure(Map("notifications.envelopeSize" -> "256B"))
    .overrides(bind[NotificationsService].to(NotificationsServiceMock.aMock))
    .overrides(bind[ConfirmationService].to(ConfirmationServiceMock.aMock))
    .overrides(bind[AuthConnector].to(AuthConnectorMock.aMock))
    .build()

  lazy implicit val mat: Materializer = app.materializer
  lazy implicit val ec = mat.executionContext

  override def beforeEach(): Unit = {
    reset(NotificationsServiceMock.aMock, ConfirmationServiceMock.aMock, AuthConnectorMock.aMock)
  }

  val jsonBody: String = "{}"
  val xmlBody: String = "<someNode/>"

  override val validHeadersJson: Map[String, String] =
    Map(validAcceptHeader, "Content-Type" -> "application/json", "X-CLIENT-ID" -> clientId.value, "user-Agent" -> "api-subscription-fields")

  private val headersWithInValidUserAgent: Map[String, String] =
    Map(validAcceptHeader, "X-CLIENT-ID" -> clientId.value, "Content-Type" -> "application/json", "user-Agent" -> "some-other-service")

  val createdDateTime: Instant = Instant.now.minus(Duration.ofDays(1))

  val notification2: Notification = Notification(
    NotificationId.random,
    boxId,
    messageContentType = MessageContentType.APPLICATION_XML,
    message = "<someXml/>",
    createdDateTime = createdDateTime.plus(Duration.ofHours(12)),
    status = NotificationStatus.ACKNOWLEDGED
  )

  "WrappedNotificationController" when {

    "saveWrappedNotification" should {
      val complicatedJson = "{\"foo\":\"bar\"}"
      val escapedComplicatedJson = "{\\\"foo\\\":\\\"bar\\\"}"

      def wrappedBody(body: String, contentType: String, version: String = "1"): String = {
        s"""{"notification":{"body":"$body","contentType":"$contentType"}, "version": "$version"}"""
      }

      def wrappedBodyWithConfirmation(body: String, contentType: String, version: String, confirmationUrl: URL, privateHeaders: List[PrivateHeader]): String = {
        val privateHeadersText = privateHeaders.map(h => s"""{"name":"${h.name}","value":"${h.value}"}""").mkString(",")
        s"""{"notification":{"body":"$body","contentType":"$contentType"}, "version": "$version", "confirmationUrl":"${confirmationUrl.toString}", "privateHeaders":[ ${privateHeadersText} ]}"""
      }

      def wrappedBodyWithConfirmationButNoHeaders(body: String, contentType: String, version: String, confirmationUrl: URL): String = {
        s"""{"notification":{"body":"$body","contentType":"$contentType"}, "version": "$version", "confirmationUrl":"${confirmationUrl.toString}"}"""
      }

      def badURL: String = {
        s"""{"notification":{"body":"{}","contentType":"application/json"}, "version": "1", "confirmationUrl": "not-valid"}"""
      }

      def badProtocol: String = {
        s"""{"notification":{"body":"{}","contentType":"application/json"}, "version": "1", "confirmationUrl": "http://example.com"}"""
      }

      "return 201 when valid json, json content type header are provided and notification successfully saved" in {
        NotificationsServiceMock.SaveNotification.Json.succeedsFor(boxId, jsonBody)

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(jsonBody, MimeTypes.JSON))
        status(result) should be(CREATED)

        NotificationsServiceMock.SaveNotification.Json.verifyCalledWith(boxId, jsonBody)

      }

      "return 201 when valid complicated json, json content type header are provided and notification successfully saved" in {
        NotificationsServiceMock.SaveNotification.Json.succeedsFor(boxId, complicatedJson)

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(escapedComplicatedJson, MimeTypes.JSON))
        status(result) should be(CREATED)

        NotificationsServiceMock.SaveNotification.Json.verifyCalledWith(boxId, complicatedJson)
      }

      "return 413 when payload is too large" in {
        val overlyLargeJsonBody: String =
          """{ "averylonglabel": "012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"}"""

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(overlyLargeJsonBody, MimeTypes.JSON))
        status(result) should be(REQUEST_ENTITY_TOO_LARGE)
      }

      "return 201 when there are zero private headers" in {
        NotificationsServiceMock.SaveNotification.Json.succeedsFor(boxId, jsonBody)
        ConfirmationServiceMock.SaveConfirmationRequest.succeeds()

        val jsonText = wrappedBodyWithConfirmationButNoHeaders(jsonBody, MimeTypes.JSON, "1", new URL("https://example.com"))

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, jsonText)
        status(result) should be(CREATED)

        NotificationsServiceMock.SaveNotification.Json.verifyCalledWith(boxId, jsonBody)
        ConfirmationServiceMock.SaveConfirmationRequest.verifyCalled()
      }

      "return 201 when there are five private headers" in {
        NotificationsServiceMock.SaveNotification.Json.succeedsFor(boxId, jsonBody)
        ConfirmationServiceMock.SaveConfirmationRequest.succeeds()

        val privateHeaders = Range.inclusive(1, 5).map(i => PrivateHeader(s"n$i", s"v$i")).toList
        val jsonText = wrappedBodyWithConfirmation(jsonBody, MimeTypes.JSON, "1", new URL("https://example.com"), privateHeaders)

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, jsonText)
        status(result) should be(CREATED)

        NotificationsServiceMock.SaveNotification.Json.verifyCalledWith(boxId, jsonBody)
        ConfirmationServiceMock.SaveConfirmationRequest.verifyCalled()
      }

      "return 400 when there are too many private headers" in {
        val privateHeaders = Range.inclusive(1, 6).map(i => PrivateHeader(s"n$i", s"v$i")).toList
        val jsonText = wrappedBodyWithConfirmation(jsonBody, MimeTypes.JSON, "1", new URL("https://example.com"), privateHeaders)

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, jsonText)
        status(result) should be(BAD_REQUEST)

        val expectedErrorBody = "Request contains more than 5 private headers"
        contentAsString(result) should include(expectedErrorBody)

        NotificationsServiceMock.verifyZeroInteractions()
        ConfirmationServiceMock.verifyZeroInteractions()
      }

      "return 201 when valid xml, xml content type header are provided and notification successfully saved" in {
        NotificationsServiceMock.SaveNotification.XML.succeedsFor(boxId, xmlBody)

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.XML))
        status(result) should be(CREATED)

        NotificationsServiceMock.SaveNotification.XML.verifyCalledWith(boxId, xmlBody)
      }

      "return 400 when invalid URL" in {
        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, badURL)
        status(result) should be(BAD_REQUEST)

        await(
          result.flatMap { entity =>
            entity.body.consumeData
          }
            .map(_.decodeString(Charset.defaultCharset()))
            .map(s => println(s))
        )

        NotificationsServiceMock.verifyZeroInteractions()
      }

      "return 400 when bad protocol" in {
        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, badProtocol)
        status(result) should be(BAD_REQUEST)

        await(
          result.flatMap { entity =>
            entity.body.consumeData
          }
            .map(_.decodeString(Charset.defaultCharset()))
            .map(s => println(s))
        )

        NotificationsServiceMock.verifyZeroInteractions()
      }

      "return 400 when version number isn't 1" in {
        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(jsonBody, MimeTypes.JSON, "2"))
        status(result) should be(BAD_REQUEST)

        NotificationsServiceMock.verifyZeroInteractions()
      }

      "return 400 when json content type header is sent but invalid json" in {
        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.JSON))
        status(result) should be(BAD_REQUEST)

        NotificationsServiceMock.verifyZeroInteractions()
      }

      "return 400 when xml content type header is sent but invalid xml" in {
        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(jsonBody, MimeTypes.XML))
        status(result) should be(BAD_REQUEST)

        NotificationsServiceMock.verifyZeroInteractions()
      }

      "return 403 when useragent header is not allowlisted" in {
        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", headersWithInValidUserAgent, wrappedBody(jsonBody, MimeTypes.JSON))
        status(result) should be(FORBIDDEN)

        NotificationsServiceMock.verifyZeroInteractions()
      }

      "return 415 when bad contentType header is sent" in {
        val result =
          doPost(s"/box/${boxId.value}/wrapped-notifications", Map("user-Agent" -> "api-subscription-fields", "Content-Type" -> "foo"), wrappedBody(xmlBody, MimeTypes.CSS))
        status(result) should be(UNSUPPORTED_MEDIA_TYPE)

        NotificationsServiceMock.verifyZeroInteractions()
      }

      "return 500 when save notification throws Duplicate Notification Exception" in {
        NotificationsServiceMock.SaveNotification.XML.failsWithDuplicate(boxId, xmlBody)

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.XML))
        status(result) should be(INTERNAL_SERVER_ERROR)
        val bodyVal = contentAsString(result)
        bodyVal shouldBe "{\"code\":\"DUPLICATE_NOTIFICATION\",\"message\":\"Unable to save Notification: duplicate found\"}"

        NotificationsServiceMock.SaveNotification.XML.verifyCalledWith(boxId, xmlBody)
      }

      "return 404 when save notification throws Box not found Exception" in {
        NotificationsServiceMock.SaveNotification.XML.failsWithBoxNotFound(boxId, xmlBody)

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.XML))
        status(result) should be(NOT_FOUND)

        NotificationsServiceMock.SaveNotification.XML.verifyCalledWith(boxId, xmlBody)
      }

      "return 500 when save notification throws Any non handled Non fatal exception" in {
        NotificationsServiceMock.SaveNotification.XML.throwsFor(boxId, xmlBody, new RuntimeException("bang"))

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.XML))
        status(result) should be(INTERNAL_SERVER_ERROR)

        NotificationsServiceMock.SaveNotification.XML.verifyCalledWith(boxId, xmlBody)
      }
    }
  }

  def stringToDateTime(dateStr: String): Instant = {
    Instant.parse(dateStr)
  }

  def stringToDateTimeLenient(dateStr: Option[String]): Option[Instant] = {
    Try[Option[Instant]] {
      dateStr.map(a => InstantFormatter.lenientFormatter.parse(a, b => Instant.from(b)))
    } match {
      case Success(x) => x
      case Failure(_) => None
    }
  }

  def doGet(uri: String, headers: Map[String, String]): Future[Result] = {
    val fakeRequest = FakeRequest(GET, uri).withHeaders(headers.toSeq: _*)
    route(app, fakeRequest).get
  }

  def doPost(uri: String, headers: Map[String, String], bodyValue: String): Future[Result] = {
    doPOSTorPUT(uri, headers, bodyValue, POST)
  }

  def doPut(uri: String, headers: Map[String, String], bodyValue: String): Future[Result] = {
    doPOSTorPUT(uri, headers, bodyValue, PUT)
  }

  private def doPOSTorPUT(uri: String, headers: Map[String, String], bodyValue: String, method: String): Future[Result] = {
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

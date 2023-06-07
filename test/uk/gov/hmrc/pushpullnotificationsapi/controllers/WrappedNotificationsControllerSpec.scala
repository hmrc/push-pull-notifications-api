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

import java.time.{Duration, Instant}
import java.util.UUID
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import akka.stream.Materializer
import org.mockito.Mockito.verifyNoInteractions
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.HeaderNames.ACCEPT
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.apiplatform.modules.common.domain.services.InstantFormatter
import uk.gov.hmrc.auth.core.AuthConnector

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.PENDING
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationsService
import java.nio.charset.Charset

class WrappedNotificationsControllerSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with BeforeAndAfterEach {

  val mockNotificationService: NotificationsService = mock[NotificationsService]
  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  override lazy val app: Application = GuiceApplicationBuilder()
    .configure(Map("notifications.maxSize" -> "50B"))
    .configure(Map("notifications.envelopeSize" -> "256B"))
    .overrides(bind[NotificationsService].to(mockNotificationService))
    .overrides(bind[AuthConnector].to(mockAuthConnector))
    .build()

  lazy implicit val mat: Materializer = app.materializer
  lazy implicit val ec = mat.executionContext

  override def beforeEach(): Unit = {
    reset(mockNotificationService, mockAuthConnector)
  }

  val clientIdStr: String = UUID.randomUUID().toString
  val clientId: ClientId = ClientId(clientIdStr)
  val incorrectClientId: String = "badclientid"
  val boxName: String = "boxName"
  val boxIdStr: String = UUID.randomUUID().toString
  val boxId: BoxId = BoxId(UUID.fromString(boxIdStr))
  val jsonBody: String = "{}"
  val xmlBody: String = "<someNode/>"

  private val validAcceptHeader = ACCEPT -> "application/vnd.hmrc.1.0+json"

  private val validHeadersJson: Map[String, String] =
    Map(validAcceptHeader, "Content-Type" -> "application/json", "X-CLIENT-ID" -> clientId.value, "user-Agent" -> "api-subscription-fields")

  private val headersWithInValidUserAgent: Map[String, String] =
    Map(validAcceptHeader, "X-CLIENT-ID" -> clientId.value, "Content-Type" -> "application/json", "user-Agent" -> "some-other-service")

  val createdDateTime: Instant = Instant.now.minus(Duration.ofDays(1))

  val notification: Notification = Notification(
    NotificationId.random,
    boxId,
    messageContentType = MessageContentType.APPLICATION_JSON,
    message = "{}",
    createdDateTime = createdDateTime,
    status = PENDING
  )

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

      def wrappedBody(body: String, contentType: String, version: String = "1"): String = {
        s"""{"notification":{"body":"$body","contentType":"$contentType"}, "version": "$version"}"""
      }

      def badURL(): String = {
        s"""{"notification":{"body":"{}","contentType":"application/json"}, "version": "1", "confirmationUrl": "not-valid"}"""
      }

      "return 201 when valid json, json content type header are provided and notification successfully saved" in {
        when(mockNotificationService.saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_JSON), eqTo(jsonBody))(*)).thenReturn(
          Future.successful(NotificationCreateSuccessResult())
        )

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(jsonBody, MimeTypes.JSON))
        status(result) should be(CREATED)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_JSON), eqTo(jsonBody))(*)
      }

      "return 201 when valid complicated json, json content type header are provided and notification successfully saved" in {
        val complicatedJson = "{\"foo\":\"bar\"}"
        val escapedComplicatedJson = "{\\\"foo\\\":\\\"bar\\\"}"
        when(mockNotificationService.saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_JSON), eqTo(complicatedJson))(*)).thenReturn(
          Future.successful(NotificationCreateSuccessResult())
        )

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(escapedComplicatedJson, MimeTypes.JSON))
        status(result) should be(CREATED)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_JSON), eqTo(complicatedJson))(*)
      }

      "return 413 when payload is too large" in {
        val overlyLargeJsonBody: String =
          """{ "averylonglabel": "012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"}"""

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(overlyLargeJsonBody, MimeTypes.JSON))
        status(result) should be(REQUEST_ENTITY_TOO_LARGE)
      }

      "return 201 when valid xml, xml content type header are provided and notification successfully saved" in {
        when(mockNotificationService
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*))
          .thenReturn(Future.successful(NotificationCreateSuccessResult()))

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.XML))
        status(result) should be(CREATED)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*)
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

        verifyNoInteractions(mockNotificationService)
      }

      "return 400 when version number isn't 1" in {
        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(jsonBody, MimeTypes.JSON, "2"))
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 400 when json content type header is sent but invalid json" in {
        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.JSON))
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 400 when xml content type header is sent but invalid xml" in {
        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(jsonBody, MimeTypes.XML))
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 403 when useragent header is not allowlisted" in {
        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", headersWithInValidUserAgent, wrappedBody(jsonBody, MimeTypes.JSON))
        status(result) should be(FORBIDDEN)

        verifyNoInteractions(mockNotificationService)
      }

      "return 415 when bad contentType header is sent" in {
        val result =
          doPost(s"/box/${boxId.value}/wrapped-notifications", Map("user-Agent" -> "api-subscription-fields", "Content-Type" -> "foo"), wrappedBody(xmlBody, MimeTypes.CSS))
        status(result) should be(UNSUPPORTED_MEDIA_TYPE)

        verifyNoInteractions(mockNotificationService)
      }

      "return 500 when save notification throws Duplicate Notification Exception" in {
        when(mockNotificationService
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*))
          .thenReturn(Future.successful(NotificationCreateFailedDuplicateResult("error")))

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.XML))
        status(result) should be(INTERNAL_SERVER_ERROR)
        val bodyVal = contentAsString(result)
        bodyVal shouldBe "{\"code\":\"DUPLICATE_NOTIFICATION\",\"message\":\"Unable to save Notification: duplicate found\"}"

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*)
      }

      "return 404 when save notification throws Box not found Exception" in {
        when(mockNotificationService
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*))
          .thenReturn(Future.successful(NotificationCreateFailedBoxIdNotFoundResult("some Exception")))

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.XML))
        status(result) should be(NOT_FOUND)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*)
      }

      "return 500 when save notification throws Any non handled Non fatal exception" in {
        when(mockNotificationService
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*))
          .thenReturn(Future.failed(new RuntimeException("some Exception")))

        val result = doPost(s"/box/${boxId.value}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.XML))
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*)
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

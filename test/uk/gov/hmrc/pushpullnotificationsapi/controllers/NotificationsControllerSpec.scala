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

import java.time.format.DateTimeFormatterBuilder
import java.time.{Duration, Instant, ZoneId}
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
import uk.gov.hmrc.auth.core.{AuthConnector, SessionRecordNotFound}

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.InstantFormatter.lenientFormatter
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.PENDING
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationsService

class NotificationsControllerSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with BeforeAndAfterEach {

  val mockNotificationService: NotificationsService = mock[NotificationsService]
  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  override lazy val app: Application = GuiceApplicationBuilder()
    .configure(Map("notifications.maxSize" -> "50B"))
    .configure(Map("notifications.envelopeSize" -> "256B"))
    .overrides(bind[NotificationsService].to(mockNotificationService))
    .overrides(bind[AuthConnector].to(mockAuthConnector))
    .build()

  lazy implicit val mat: Materializer = app.materializer

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
  private val invalidAcceptHeader = ACCEPT -> "application/vnd.hmrc.2.0+json"

  private val validHeadersJson: Map[String, String] =
    Map(validAcceptHeader, "Content-Type" -> "application/json", "X-CLIENT-ID" -> clientId.value, "user-Agent" -> "api-subscription-fields")

  private val validHeadersXml: Map[String, String] =
    Map(validAcceptHeader, "Content-Type" -> "application/xml", "X-CLIENT-ID" -> clientId.value, "user-Agent" -> "api-subscription-fields")

  private val headersWithInValidUserAgent: Map[String, String] =
    Map(validAcceptHeader, "X-CLIENT-ID" -> clientId.value, "Content-Type" -> "application/json", "user-Agent" -> "some-other-service")

  private val headersWithInvalidContentType: Map[String, String] =
    Map(validAcceptHeader, "Content-Type" -> "foo", "X-CLIENT-ID" -> clientId.value, "user-Agent" -> "api-subscription-fields")

  val createdDateTime: Instant = Instant.now.minus(Duration.ofDays(1))

  val notification: Notification = Notification(
    NotificationId(UUID.randomUUID()),
    boxId,
    messageContentType = MessageContentType.APPLICATION_JSON,
    message = "{}",
    createdDateTime = createdDateTime,
    status = PENDING
  )

  val notification2: Notification = Notification(
    NotificationId(UUID.randomUUID()),
    boxId,
    messageContentType = MessageContentType.APPLICATION_XML,
    message = "<someXml/>",
    createdDateTime = createdDateTime.plus(Duration.ofHours(12)),
    status = NotificationStatus.ACKNOWLEDGED
  )

  "NotificationController" when {
    "saveNotification" should {
      "return 201 when valid json, json content type header are provided and notification successfully saved" in {
        when(mockNotificationService.saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_JSON), eqTo(jsonBody))(*, *)).thenReturn(
          Future.successful(NotificationCreateSuccessResult())
        )

        val result = doPost(s"/box/${boxId.value.toString}/notifications", validHeadersJson, jsonBody)
        status(result) should be(CREATED)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_JSON), eqTo(jsonBody))(*, *)
      }

      "fail when payload is too large" in {
        val overlyLargeJsonBody: String =
          """{ "averylonglabel": "012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"}"""

        val result = doPost(s"/box/${boxId.value.toString}/notifications", validHeadersJson, overlyLargeJsonBody)
        status(result) should be(REQUEST_ENTITY_TOO_LARGE)
      }

      "return 201 when valid xml, xml content type header are provided and notification successfully saved" in {
        when(mockNotificationService
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *))
          .thenReturn(Future.successful(NotificationCreateSuccessResult()))

        val result = doPost(s"/box/${boxId.value.toString}/notifications", validHeadersXml, xmlBody)
        status(result) should be(CREATED)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *)
      }

      "return 400 when json content type header is sent but invalid json" in {
        val result = doPost(s"/box/${boxId.value.toString}/notifications", validHeadersJson, xmlBody)
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 400 when xml content type header is sent but invalid xml" in {
        val result = doPost(s"/box/${boxId.value.toString}/notifications", validHeadersXml, jsonBody)
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 403 when useragent header is not allowlisted" in {
        val result = doPost(s"/box/${boxId.value.toString}/notifications", headersWithInValidUserAgent, jsonBody)
        status(result) should be(FORBIDDEN)

        verifyNoInteractions(mockNotificationService)
      }

      "return 415 when bad contentType header is sent" in {
        val result = doPost(s"/box/${boxId.value.toString}/notifications", Map("user-Agent" -> "api-subscription-fields", "Content-Type" -> "foo"), jsonBody)
        status(result) should be(UNSUPPORTED_MEDIA_TYPE)

        verifyNoInteractions(mockNotificationService)
      }

      "return 500 when save notification throws Duplicate Notification Exception" in {
        when(mockNotificationService
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *))
          .thenReturn(Future.successful(NotificationCreateFailedDuplicateResult("error")))

        val result = doPost(s"/box/${boxId.value.toString}/notifications", validHeadersXml, xmlBody)
        status(result) should be(INTERNAL_SERVER_ERROR)
        val bodyVal = contentAsString(result)
        bodyVal shouldBe "{\"code\":\"DUPLICATE_NOTIFICATION\",\"message\":\"Unable to save Notification: duplicate found\"}"

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *)
      }

      "return 404 when save notification throws Box not found Exception" in {
        when(mockNotificationService
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *))
          .thenReturn(Future.successful(NotificationCreateFailedBoxIdNotFoundResult("some Exception")))

        val result = doPost(s"/box/${boxId.value.toString}/notifications", validHeadersXml, xmlBody)
        status(result) should be(NOT_FOUND)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *)
      }

      "return 500 when save notification throws Any non handled Non fatal exception" in {
        when(mockNotificationService
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *))
          .thenReturn(Future.failed(new RuntimeException("some Exception")))

        val result = doPost(s"/box/${boxId.value.toString}/notifications", validHeadersXml, xmlBody)
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *)
      }
    }

    "saveWrappedNotification" should {

      def wrappedBody(body: String, contentType: String, version: String = "1"): String = {
        s"""{"notification":{"body":"$body","contentType":"$contentType"}, "version": "$version"}"""
      }

      "return 201 when valid json, json content type header are provided and notification successfully saved" in {
        when(mockNotificationService.saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_JSON), eqTo(jsonBody))(*, *)).thenReturn(
          Future.successful(NotificationCreateSuccessResult())
        )

        val result = doPost(s"/box/${boxId.value.toString}/wrapped-notifications", validHeadersJson, wrappedBody(jsonBody, MimeTypes.JSON))
        status(result) should be(CREATED)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_JSON), eqTo(jsonBody))(*, *)
      }

      "return 201 when valid complicated json, json content type header are provided and notification successfully saved" in {
        val complicatedJson = "{\"foo\":\"bar\"}"
        val escapedComplicatedJson = "{\\\"foo\\\":\\\"bar\\\"}"
        when(mockNotificationService.saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_JSON), eqTo(complicatedJson))(*, *)).thenReturn(
          Future.successful(NotificationCreateSuccessResult())
        )

        val result = doPost(s"/box/${boxId.value.toString}/wrapped-notifications", validHeadersJson, wrappedBody(escapedComplicatedJson, MimeTypes.JSON))
        status(result) should be(CREATED)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_JSON), eqTo(complicatedJson))(*, *)
      }

      "return 413 when payload is too large" in {
        val overlyLargeJsonBody: String =
          """{ "averylonglabel": "012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"}"""

        val result = doPost(s"/box/${boxId.value.toString}/wrapped-notifications", validHeadersJson, wrappedBody(overlyLargeJsonBody, MimeTypes.JSON))
        status(result) should be(REQUEST_ENTITY_TOO_LARGE)
      }

      "return 201 when valid xml, xml content type header are provided and notification successfully saved" in {
        when(mockNotificationService
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *))
          .thenReturn(Future.successful(NotificationCreateSuccessResult()))

        val result = doPost(s"/box/${boxId.value.toString}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.XML))
        status(result) should be(CREATED)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *)
      }

      "return 400 when version number isn't 1" in {
        val result = doPost(s"/box/${boxId.value.toString}/wrapped-notifications", validHeadersJson, wrappedBody(jsonBody, MimeTypes.JSON, "2"))
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 400 when json content type header is sent but invalid json" in {
        val result = doPost(s"/box/${boxId.value.toString}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.JSON))
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 400 when xml content type header is sent but invalid xml" in {
        val result = doPost(s"/box/${boxId.value.toString}/wrapped-notifications", validHeadersJson, wrappedBody(jsonBody, MimeTypes.XML))
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 403 when useragent header is not allowlisted" in {
        val result = doPost(s"/box/${boxId.value.toString}/wrapped-notifications", headersWithInValidUserAgent, wrappedBody(jsonBody, MimeTypes.JSON))
        status(result) should be(FORBIDDEN)

        verifyNoInteractions(mockNotificationService)
      }

      "return 415 when bad contentType header is sent" in {
        val result = doPost(s"/box/${boxId.value.toString}/wrapped-notifications", Map("user-Agent" -> "api-subscription-fields", "Content-Type" -> "foo"), wrappedBody(xmlBody, MimeTypes.CSS))
        status(result) should be(UNSUPPORTED_MEDIA_TYPE)

        verifyNoInteractions(mockNotificationService)
      }

      "return 500 when save notification throws Duplicate Notification Exception" in {
        when(mockNotificationService
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *))
          .thenReturn(Future.successful(NotificationCreateFailedDuplicateResult("error")))

        val result = doPost(s"/box/${boxId.value.toString}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.XML))
        status(result) should be(INTERNAL_SERVER_ERROR)
        val bodyVal = contentAsString(result)
        bodyVal shouldBe "{\"code\":\"DUPLICATE_NOTIFICATION\",\"message\":\"Unable to save Notification: duplicate found\"}"

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *)
      }

      "return 404 when save notification throws Box not found Exception" in {
        when(mockNotificationService
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *))
          .thenReturn(Future.successful(NotificationCreateFailedBoxIdNotFoundResult("some Exception")))

        val result = doPost(s"/box/${boxId.value.toString}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.XML))
        status(result) should be(NOT_FOUND)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *)
      }

      "return 500 when save notification throws Any non handled Non fatal exception" in {
        when(mockNotificationService
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *))
          .thenReturn(Future.failed(new RuntimeException("some Exception")))

        val result = doPost(s"/box/${boxId.value.toString}/wrapped-notifications", validHeadersJson, wrappedBody(xmlBody, MimeTypes.XML))
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockNotificationService)
          .saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*, *)
      }
    }

    "getNotificationsByBoxIdAndFilters" should {
      "return 200 and list of matching notifications when status filter provided" in {
        testAndValidateGetByQueryParams(boxId, OK, Some("ACKNOWLEDGED"))
      }

      "not return retryAfterDateTime" in {
        primeAuthAction(clientIdStr)
        when(mockNotificationService.getNotifications(
          eqTo(boxId),
          eqTo(clientId),
          eqTo(None),
          eqTo(None),
          eqTo(None)
        )(*))
          .thenReturn(Future.successful(GetNotificationsSuccessRetrievedResult(List(notification.copy(retryAfterDateTime = Some(Instant.now))))))

        val result = doGet(s"/box/${boxId.value.toString}/notifications", validHeadersJson)

        contentAsString(result).contains("retryAfterDateTime") shouldBe false
      }

      "not expand value classes" in {
        primeAuthAction(clientIdStr)
        when(mockNotificationService.getNotifications(
          eqTo(boxId),
          eqTo(clientId),
          eqTo(None),
          eqTo(None),
          eqTo(None)
        )(*))
          .thenReturn(Future.successful(GetNotificationsSuccessRetrievedResult(List(notification))))

        val result = doGet(s"/box/${boxId.value.toString}/notifications", validHeadersJson)

        val resultStr = contentAsString(result)
        val expectedCreatedDateTime = new DateTimeFormatterBuilder()
          .appendPattern("uuuu-MM-dd'T'HH:mm:ss.SSSZ")
          .toFormatter
          .withZone(ZoneId.of("UTC"))
          .format(createdDateTime)
        resultStr.contains(s""""notificationId":"${notification.notificationId.value}"""") shouldBe true
        resultStr.contains(s""""boxId":"${notification.boxId.value}"""") shouldBe true
        resultStr.contains(s""""createdDateTime":"$expectedCreatedDateTime"""") shouldBe true

      }

      "return 200 list of notification when no query parameters are provided" in {
        primeAuthAction(clientIdStr)
        when(mockNotificationService.getNotifications(
          eqTo(boxId),
          eqTo(clientId),
          eqTo(None),
          eqTo(None),
          eqTo(None)
        )(*))
          .thenReturn(Future.successful(GetNotificationsSuccessRetrievedResult(List(notification, notification2))))

        val result = doGet(s"/box/${boxId.value.toString}/notifications", validHeadersJson)
        status(result) shouldBe OK
        val resultStr = contentAsString(result)
        resultStr.contains("\"messageContentType\":\"application/json\"") shouldBe true
        resultStr.contains("\"messageContentType\":\"application/xml\"") shouldBe true
      }

      "return 400 when invalid status filter provided" in {
        testAndValidateGetByQueryParams(boxId, BAD_REQUEST, Some("KBUO"))
      }

      "return 200 when valid fromDate filter provided" in {
        testAndValidateGetByQueryParams(boxId, OK, None, maybeFromDateStr = Some("2020-02-02T00:54:00Z"))
      }

      "return 200 when valid status, fromDate filter are provided" in {
        testAndValidateGetByQueryParams(boxId, OK, Some("PENDING"), maybeFromDateStr = Some("2020-02-02T00:54:00Z"))
      }

      "return 400 when invalid fromDate filter provided" in {
        testAndValidateGetByQueryParams(boxId, BAD_REQUEST, None, maybeFromDateStr = Some("4433:33:88T223322"))
      }

      "return 200 when valid toDate filter provided" in {
        testAndValidateGetByQueryParams(boxId, OK, None, maybeToDateStr = Some("2020-02-02T00:54:00Z"))
      }

      "return 200 when valid param filter with just date" in {
        testAndValidateGetByQueryParams(boxId, OK, None, maybeToDateStr = Some("2020-02-02"))
      }
      "return 200 when valid param filter without Z" in {
        testAndValidateGetByQueryParams(boxId, OK, None, maybeToDateStr = Some("2020-02-02T00:54:00"))
      }

      "return 200 when valid toDate and status filters are provided" in {
        testAndValidateGetByQueryParams(boxId, OK, Some("PENDING"), maybeToDateStr = Some("2020-02-02T00:54:00Z"))
      }

      "return 400 when invalid toDate filter provided" in {
        testAndValidateGetByQueryParams(boxId, BAD_REQUEST, None, maybeToDateStr = Some("4433:33:88T223322"))

      }

      "return 400 when fromdate is after toDate" in {
        val fromdateStr = "2020-02-05T00:54:00Z"
        val toDateStr = "2020-02-02T00:54:00Z"
        testAndValidateGetByQueryParams(boxId, BAD_REQUEST, None, maybeFromDateStr = Some(fromdateStr), maybeToDateStr = Some(toDateStr))
      }

      "return 400 when invalid status query parameter is provided" in {
        primeAuthAction(clientIdStr)
        val result = doGet(s"/box/${boxId.value.toString}/notifications?status=SOMEVALUE", validHeadersJson)
        status(result) shouldBe BAD_REQUEST
        val resultStr = contentAsString(result)
        resultStr shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"Invalid Status parameter provided\"}"
      }

      "return 400 when unknown query parameter is provided" in {
        primeAuthAction(clientIdStr)
        val result = doGet(s"/box/${boxId.value.toString}/notifications?IamUnknown=whatever", validHeadersJson)
        status(result) shouldBe BAD_REQUEST
        val resultStr = contentAsString(result)
        resultStr shouldBe "{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"message\":\"Invalid / Unknown query parameter provided\"}"
      }

      "return 404 when boxId is not found does not match" in {
        val fromdatStr = "2020-02-02T00:54:00Z"
        val toDateStr = "2020-02-03T00:54:00Z"
        primeAuthAction(UUID.randomUUID().toString)
        when(mockNotificationService.getNotifications(
          eqTo(boxId),
          *[ClientId],
          eqTo(Some(PENDING)),
          eqTo(stringToDateTimeLenient(Some(fromdatStr))),
          eqTo(stringToDateTimeLenient(Some(toDateStr)))
        )(*))
          .thenReturn(Future.successful(GetNotificationsServiceBoxNotFoundResult("")))

        val result = doGet(s"/box/${boxId.value.toString}/notifications?status=PENDING&fromDate=$fromdatStr&toDate=$toDateStr", validHeadersJson)
        status(result) shouldBe NOT_FOUND
        contentAsString(result) shouldBe "{\"code\":\"BOX_NOT_FOUND\",\"message\":\"Box not found\"}"
      }

      "return 401 when clientId does not match" in {
        val fromdatStr = "2020-02-02T00:54:00Z"
        val toDateStr = "2020-02-03T00:54:00Z"
        primeAuthAction(UUID.randomUUID().toString)
        when(mockNotificationService.getNotifications(
          eqTo(boxId),
          *[ClientId],
          eqTo(Some(PENDING)),
          eqTo(stringToDateTimeLenient(Some(fromdatStr))),
          eqTo(stringToDateTimeLenient(Some(toDateStr)))
        )(*))
          .thenReturn(Future.successful(GetNotificationsServiceUnauthorisedResult("")))

        val result = doGet(s"/box/${boxId.value.toString}/notifications?status=PENDING&fromDate=$fromdatStr&toDate=$toDateStr", validHeadersJson)
        status(result) shouldBe FORBIDDEN
      }

      "return 200 with empty List when no notifications returned" in {
        val fromdatStr = "2020-02-02T00:54:00Z"
        val toDateStr = "2020-02-03T00:54:00Z"
        primeAuthAction(clientIdStr)
        when(mockNotificationService.getNotifications(
          eqTo(boxId),
          eqTo(clientId),
          eqTo(Some(PENDING)),
          eqTo(stringToDateTimeLenient(Some(fromdatStr))),
          eqTo(stringToDateTimeLenient(Some(toDateStr)))
        )(*))
          .thenReturn(Future.successful(GetNotificationsSuccessRetrievedResult(List.empty)))

        val result = doGet(s"/box/${boxId.value.toString}/notifications?status=PENDING&fromDate=$fromdatStr&toDate=$toDateStr", validHeadersJson)
        status(result) shouldBe OK
      }

      "return 401 when no clientId is returned from auth" in {
        when(mockAuthConnector.authorise[Option[String]](*, *)(*, *))
          .thenReturn(Future.successful(None))

        val result = doGet(s"/box/${boxId.value.toString}/notifications", validHeadersJson)
        status(result) shouldBe UNAUTHORIZED
      }

      "return 401 when authorisation fails" in {
        when(mockAuthConnector.authorise[Option[String]](*, *)(*, *))
          .thenReturn(Future.failed(SessionRecordNotFound()))

        val result = doGet(s"/box/${boxId.value.toString}/notifications", validHeadersJson)
        status(result) shouldBe UNAUTHORIZED
      }

      "return 406 when accept header is missing" in {
        val result = doGet(s"/box/${boxId.value.toString}/notifications", validHeadersJson - ACCEPT)

        status(result) shouldBe NOT_ACCEPTABLE
        (contentAsJson(result) \ "code").as[String] shouldBe "ACCEPT_HEADER_INVALID"
        (contentAsJson(result) \ "message").as[String] shouldBe "The accept header is missing or invalid"
      }

      "return 406 when accept header is invalid" in {
        val result = doGet(s"/box/${boxId.value.toString}/notifications", validHeadersJson - ACCEPT + invalidAcceptHeader)

        status(result) shouldBe NOT_ACCEPTABLE
        (contentAsJson(result) \ "code").as[String] shouldBe "ACCEPT_HEADER_INVALID"
        (contentAsJson(result) \ "message").as[String] shouldBe "The accept header is missing or invalid"
      }
    }

    "acknowledgeNotifications" should {

      "return 200 when acknowledge request is valid " in {
        primeAuthAction(clientIdStr)
        when(mockNotificationService.acknowledgeNotifications(*[BoxId], *[ClientId], *)(*, *))
          .thenReturn(Future.successful(AcknowledgeNotificationsSuccessUpdatedResult(true)))
        val validatedAcknowledgeRequest = "{\"notificationIds\": [\"2e0cf493-0d3e-4dae-a200-b17e76ff547f\", \"de396b71-55c7-4a24-954a-df6bd4a85795\"]}"
        val result = doPut(s"/box/${boxId.value.toString}/notifications/acknowledge", validHeadersJson, validatedAcknowledgeRequest)
        status(result) shouldBe NO_CONTENT
      }

      "return 403 when service returns unauthorised result" in {
        primeAuthAction(clientIdStr)
        when(mockNotificationService.acknowledgeNotifications(*[BoxId], *[ClientId], *)(*, *))
          .thenReturn(Future.successful(AcknowledgeNotificationsServiceUnauthorisedResult("some message")))
        val validatedAcknowledgeRequest = "{\"notificationIds\": [\"2e0cf493-0d3e-4dae-a200-b17e76ff547f\", \"de396b71-55c7-4a24-954a-df6bd4a85795\"]}"
        val result = doPut(s"/box/${boxId.value.toString}/notifications/acknowledge", validHeadersJson, validatedAcknowledgeRequest)
        status(result) shouldBe FORBIDDEN
      }

      "return 404 when service returns box not found result" in {
        primeAuthAction(clientIdStr)
        when(mockNotificationService.acknowledgeNotifications(*[BoxId], *[ClientId], *)(*, *))
          .thenReturn(Future.successful(AcknowledgeNotificationsServiceBoxNotFoundResult("some message")))
        val validatedAcknowledgeRequest = "{\"notificationIds\": [\"2e0cf493-0d3e-4dae-a200-b17e76ff547f\", \"de396b71-55c7-4a24-954a-df6bd4a85795\"]}"
        val result = doPut(s"/box/${boxId.value.toString}/notifications/acknowledge", validHeadersJson, validatedAcknowledgeRequest)
        status(result) shouldBe NOT_FOUND
      }

      "return 403 when acknowledge request is valid but service return unauthorised" in {
        primeAuthAction(clientIdStr)
        when(mockNotificationService.acknowledgeNotifications(*[BoxId], *[ClientId], *)(*, *))
          .thenReturn(Future.successful(AcknowledgeNotificationsServiceUnauthorisedResult("some message")))
        val validatedAcknowledgeRequest = "{\"notificationIds\": [\"2e0cf493-0d3e-4dae-a200-b17e76ff547f\", \"de396b71-55c7-4a24-954a-df6bd4a85795\"]}"
        val result = doPut(s"/box/${boxId.value.toString}/notifications/acknowledge", validHeadersJson, validatedAcknowledgeRequest)
        status(result) shouldBe FORBIDDEN
      }

      "return 406 when invalid accept header is provided" in {
        primeAuthAction(clientIdStr)
        when(mockNotificationService.acknowledgeNotifications(*[BoxId], *[ClientId], *)(*, *))
          .thenReturn(Future.successful(AcknowledgeNotificationsServiceUnauthorisedResult("some message")))
        val validatedAcknowledgeRequest = "{\"notificationIds\": [\"2e0cf493-0d3e-4dae-a200-b17e76ff547f\", \"de396b71-55c7-4a24-954a-df6bd4a85795\"]}"
        val result = doPut(s"/box/${boxId.value.toString}/notifications/acknowledge", validHeadersJson - ACCEPT + invalidAcceptHeader, validatedAcknowledgeRequest)
        status(result) shouldBe NOT_ACCEPTABLE
      }

      "return 400 when acknowledge request is not valid against the model" in {
        primeAuthAction(clientIdStr)
        val request = "{\"somINvalidKey\": [\"222222\"]}"
        val result = doPut(s"/box/${boxId.value.toString}/notifications/acknowledge", validHeadersJson, request)
        status(result) shouldBe BAD_REQUEST
        (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_REQUEST_PAYLOAD"
        (contentAsJson(result) \ "message").as[String] shouldBe "JSON body is invalid against expected format"
        verifyNoInteractions(mockNotificationService)
      }

      "return 400 when request contains and invalid(nonUUID) notificationID" in {
        primeAuthAction(clientIdStr)
        val request = "{\"notificationIds\": [\"22222222\"]}"
        val result = doPut(s"/box/${boxId.value.toString}/notifications/acknowledge", validHeadersJson, request)
        status(result) shouldBe BAD_REQUEST
        (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_REQUEST_PAYLOAD"
        (contentAsJson(result) \ "message").as[String] shouldBe "JSON body is invalid against expected format"
      }

      "return 400 when request contains no ids" in {
        primeAuthAction(clientIdStr)
        val request = "{\"notificationIds\": []}"
        val result = doPut(s"/box/${boxId.value.toString}/notifications/acknowledge", validHeadersJson, request)
        status(result) shouldBe BAD_REQUEST
        (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_REQUEST_PAYLOAD"
        (contentAsJson(result) \ "message").as[String] shouldBe "JSON body is invalid against expected format"
        verifyNoInteractions(mockNotificationService)
      }

      "return 400 when acknowledge request contains duplicates" in {
        primeAuthAction(clientIdStr)
        val validatedAcknowledgeRequest = "{\"notificationIds\": [\"de396b71-55c7-4a24-954a-df6bd4a85795\", \"de396b71-55c7-4a24-954a-df6bd4a85795\"]}"
        val result = doPut(s"/box/${boxId.value.toString}/notifications/acknowledge", validHeadersJson, validatedAcknowledgeRequest)
        status(result) shouldBe BAD_REQUEST
        (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_REQUEST_PAYLOAD"
        (contentAsJson(result) \ "message").as[String] shouldBe "JSON body is invalid against expected format"
        verifyNoInteractions(mockNotificationService)
      }

      "return 415 if Content-Type header is invalid" in {
        val validatedAcknowledgeRequest = "{\"notificationIds\": [\"2e0cf493-0d3e-4dae-a200-b17e76ff547f\", \"de396b71-55c7-4a24-954a-df6bd4a85795\"]}"
        val result = doPut(s"/box/${boxId.value.toString}/notifications/acknowledge", headersWithInvalidContentType, validatedAcknowledgeRequest)
        status(result) should be(UNSUPPORTED_MEDIA_TYPE)

        val jsonBody = contentAsJson(result)
        (jsonBody \ "code").as[String] shouldBe "BAD_REQUEST"
      }
    }
  }

  private def primeAuthAction(clientId: String): Unit = {
    when(mockAuthConnector.authorise[Option[String]](*, *)(*, *)).thenReturn(Future.successful(Some(clientId)))

  }

  private def testAndValidateGetByQueryParams(
      boxId: BoxId,
      expectedStatusCode: Int,
      maybeNotificationStatus: Option[String],
      maybeFromDateStr: Option[String] = None,
      maybeToDateStr: Option[String] = None
    ): Unit = {
    if (expectedStatusCode.equals(UNAUTHORIZED)) {
      when(mockAuthConnector.authorise[Option[String]](*, *)(*, *)).thenReturn(Future.successful(None))
    } else {
      primeAuthAction(clientIdStr)
    }
    val maybeFromDate: Option[Instant] = stringToDateTimeLenient(maybeFromDateStr)
    val maybeToDate: Option[Instant] = stringToDateTimeLenient(maybeToDateStr)

    expectedStatusCode match {
      case OK          => when(mockNotificationService.getNotifications(
          eqTo(boxId),
          eqTo(clientId),
          eqTo(maybeNotificationStatus.map(NotificationStatus.withName)),
          eqTo(maybeFromDate),
          eqTo(maybeToDate)
        )(*))
          .thenReturn(Future.successful(GetNotificationsSuccessRetrievedResult(List(notification, notification2))))
      case NOT_FOUND   => ()
      case BAD_REQUEST => ()
    }

    val statusQueryString = maybeNotificationStatus.fold("")(x => s"status=$x&")
    val toDateQueryString = maybeToDateStr.fold("")(x => s"toDate=$x&")
    val fromDateQueryString = maybeFromDateStr.fold("")(x => s"fromDate=$x&")

    val result = doGet(s"/box/${boxId.value.toString}/notifications?" ++ statusQueryString ++ fromDateQueryString ++ toDateQueryString, validHeadersJson)
    status(result) shouldBe expectedStatusCode

    expectedStatusCode match {
      case NOT_FOUND   => verifyNoInteractions(mockNotificationService)
      case BAD_REQUEST => verifyNoInteractions(mockNotificationService)
      case OK          => verify(mockNotificationService).getNotifications(
          eqTo(boxId),
          eqTo(clientId),
          eqTo(maybeNotificationStatus.map(NotificationStatus.withName)),
          eqTo(maybeFromDate),
          eqTo(maybeToDate)
        )(*)
    }
  }

  def stringToDateTime(dateStr: String): Instant = {
    Instant.parse(dateStr)
  }

  def stringToDateTimeLenient(dateStr: Option[String]): Option[Instant] = {
    Try[Option[Instant]] {
      dateStr.map(a => lenientFormatter.parse(a, b => Instant.from(b)))
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

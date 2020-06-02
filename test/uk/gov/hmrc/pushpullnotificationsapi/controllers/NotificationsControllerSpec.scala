/*
 * Copyright 2020 HM Revenue & Customs
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

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{reset, verify, verifyNoInteractions, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_REQUEST, POST, route, _}
import uk.gov.hmrc.auth.core.{AuthConnector, SessionRecordNotFound}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.{RECEIVED, UNKNOWN}
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationContentType, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.models.{DuplicateNotificationException, TopicNotFoundException}
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationsService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class NotificationsControllerSpec extends UnitSpec with MockitoSugar
  with GuiceOneAppPerSuite with BeforeAndAfterEach {

  val mockNotificationService: NotificationsService = mock[NotificationsService]
  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[NotificationsService].to(mockNotificationService))
    .overrides(bind[AuthConnector].to(mockAuthConnector))
    .build()

  override def beforeEach(): Unit = {
    reset(mockNotificationService, mockAuthConnector)
  }

  val clientId: String = "clientid"
  val incorrectClientId: String = "badclientid"
  val topicName: String = "topicName"
  val topicId: String = UUID.randomUUID().toString
  val jsonBody: String = "{}"
  val xmlBody: String = "<someNode/>"

  private val validHeadersJson: Map[String, String] = Map("Content-Type" -> "application/json", "X-CLIENT-ID" -> clientId)
  private val validHeadersXml: Map[String, String] = Map("Content-Type" -> "application/xml", "X-CLIENT-ID" -> clientId)

  val createdDateTime: DateTime = DateTime.now().minusDays(1)
  val notification: Notification = Notification(UUID.randomUUID(), topicId,
    notificationContentType = NotificationContentType.APPLICATION_JSON,
    message = "{}",
    createdDateTime = createdDateTime,
    status = RECEIVED)

  val notification2: Notification = Notification(UUID.randomUUID(), topicId,
    notificationContentType = NotificationContentType.APPLICATION_XML,
    message = "<someXml/>",
    createdDateTime = createdDateTime.plusHours(12),
    status = NotificationStatus.READ)


  "NotificationController" when {
    "saveNotification" should {
      "return 201 when valid json, json content type header are provided and notification successfully saved" in {
        when(mockNotificationService.saveNotification(eqTo(topicId),
          any[UUID],
          eqTo(NotificationContentType.APPLICATION_JSON),
          eqTo(jsonBody))(any[ExecutionContext])).thenReturn(Future.successful(true))

        val result = doPost(s"/notifications/topics/$topicId", validHeadersJson, jsonBody)
        status(result) should be(CREATED)

        verify(mockNotificationService)
          .saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_JSON), eqTo(jsonBody))(any[ExecutionContext])
      }

      "return 201 when valid xml, xml content type header are provided and notification successfully saved" in {
        when(mockNotificationService
          .saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.successful(true))

        val result = doPost(s"/notifications/topics/$topicId", validHeadersXml, xmlBody)
        status(result) should be(CREATED)

        verify(mockNotificationService)
          .saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }

      "return 400 when json content type header is sent but invalid json" in {

        val result = doPost(s"/notifications/topics/$topicId", validHeadersJson, xmlBody)
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 400 when xml content type header is sent but invalid xml" in {

        val result = doPost(s"/notifications/topics/$topicId", validHeadersXml, jsonBody)
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 400 when no contentType header is sent" in {

        val result = doPost(s"/notifications/topics/$topicId", Map.empty, "jsonBody")
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 500 when save notification Fails" in {

        when(mockNotificationService
          .saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.successful(false))

        val result = doPost(s"/notifications/topics/$topicId", validHeadersXml, xmlBody)
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockNotificationService)
          .saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }

      "return 422 when save notification throws Duplicate Notification Exception" in {

        when(mockNotificationService
          .saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.failed(DuplicateNotificationException("some Exception")))

        val result = await(doPost(s"/notifications/topics/$topicId", validHeadersXml, xmlBody))
        status(result) should be(UNPROCESSABLE_ENTITY)


        verify(mockNotificationService)
          .saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }

      "return 404 when save notification throws Topic not found Exception" in {

        when(mockNotificationService
          .saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.failed(TopicNotFoundException("some Exception")))

        val result = doPost(s"/notifications/topics/$topicId", validHeadersXml, xmlBody)
        status(result) should be(NOT_FOUND)

        verify(mockNotificationService)
          .saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }

      "return 500 when save notification throws Any non handled Non fatal exception" in {

        when(mockNotificationService
          .saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.failed(new RuntimeException("some Exception")))

        val result = doPost(s"/notifications/topics/$topicId", validHeadersXml, xmlBody)
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockNotificationService)
          .saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }
    }

    "getNotificationsByTopicIdAndFilters" should {

      "return 200 and list of matching notifications when no filters provided" in {
        testAndValidateGetByQueryParams(topicId,  OK, None)
      }

      "return 200 and list of matching notifications when status filter provided" in {
        testAndValidateGetByQueryParams(topicId,  OK, Some("READ"))
      }

      "return 404 when invalid status filter provided" in {
        testAndValidateGetByQueryParams(topicId,  NOT_FOUND, Some("KBUO"))
      }

      "return 200 when valid from_date filter provided" in {
        testAndValidateGetByQueryParams(topicId,  OK, None, maybeFromDateStr = Some("2020-02-02T00:54:00Z"))
      }

      "return 200 when valid status, from_date filter are provided" in {
        testAndValidateGetByQueryParams(topicId,  OK, Some("RECEIVED"), maybeFromDateStr = Some("2020-02-02T00:54:00Z"))
      }

      "return 404 when invalid from_date filter provided" in {
        testAndValidateGetByQueryParams(topicId, NOT_FOUND, None, maybeFromDateStr = Some("4433:33:88T223322"))
      }

      "return 200 when valid to_date filter provided" in {
        testAndValidateGetByQueryParams(topicId,  OK, None, maybeToDateStr = Some("2020-02-02T00:54:00Z"))
      }

      "return 200 when valid to_date and status filters are provided" in {
        testAndValidateGetByQueryParams(topicId, OK, Some("RECEIVED"), maybeToDateStr = Some("2020-02-02T00:54:00Z"))
      }

      "return 404 when invalid to_date filter provided" in {
        testAndValidateGetByQueryParams(topicId,  NOT_FOUND, None, maybeToDateStr = Some("4433:33:88T223322"))

      }

      "return 400 when clientId does not match" in {
        testAndValidateGetByQueryParams(topicId,  NOT_FOUND, None, maybeToDateStr = Some("4433:33:88T223322"))
      }

      "return 200 with empty List when no notifictions returned" in{
        val fromdatStr = "2020-02-02T00:54:00Z"
        val toDateStr = "2020-02-02T00:54:00Z"
        primeAuthAction(clientId)
        when(mockNotificationService.getNotifications(
          eqTo(topicId),
          eqTo(clientId),
          eqTo(Some(RECEIVED)),
          eqTo(stringToDateTimeLenient(Some(fromdatStr))),
          eqTo(stringToDateTimeLenient(Some(toDateStr))))(any[ExecutionContext]))
          .thenReturn(Future.successful(List.empty))

        val result = await(doGet(s"/notifications/topics/$topicId?status=RECEIVED&from_date=$fromdatStr&to_date=$toDateStr", validHeadersJson))
        status(result) shouldBe OK
      }

      "return 400 when clientId missing in header" in {
        testAndValidateGetByQueryParams(topicId, NOT_FOUND, None, maybeToDateStr = Some("4433:33:88T223322"))

      }

      "return 401 when no clientId is returned from auth" in {
        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.successful(None))
        val result = await(doGet(s"/notifications/topics/$topicId", validHeadersJson))
        status(result) shouldBe UNAUTHORIZED
      }

      "return 401 when authorisation fails" in {
        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any()))
          .thenReturn(Future.failed(SessionRecordNotFound()))

        val result = await(doGet(s"/notifications/topics/$topicId", validHeadersJson))
        status(result) shouldBe UNAUTHORIZED
      }
    }
  }

  private def primeAuthAction(clientId: String): Unit ={
    when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.successful(Some(clientId)))

  }

  private def testAndValidateGetByQueryParams(topicId: String,
                                        expectedStatusCode: Int,
                                        maybeNotificationStatus: Option[String],
                                        maybeFromDateStr: Option[String] = None,
                                        maybeToDateStr: Option[String] = None): Unit = {
    primeAuthAction(clientId)
      val maybeFromDate: Option[DateTime] = stringToDateTimeLenient(maybeFromDateStr)
      val maybeToDate: Option[DateTime] = stringToDateTimeLenient(maybeToDateStr)

      val maybeStatus = maybeNotificationStatus.map(x => {
        Try[NotificationStatus] {
          NotificationStatus.withName(x)
        } match {
          case Success(x) => x
          case Failure(_) => UNKNOWN
        }
      })

      expectedStatusCode match {
        case OK => when(mockNotificationService.getNotifications(
          eqTo(topicId),
          eqTo(clientId),
          eqTo(maybeStatus),
          eqTo(maybeFromDate),
          eqTo(maybeToDate))(any[ExecutionContext])).thenReturn(Future.successful(List(notification, notification2)))
        case NOT_FOUND => ()
      }

      val statusQueryString = maybeNotificationStatus.fold("")(x => s"status=$x&")
      val toDateQueryString = maybeToDateStr.fold("")(x => s"to_date=$x&")
      val fromDateQueryString = maybeFromDateStr.fold("")(x => s"from_date=$x&")

      val result = await(doGet(s"/notifications/topics/$topicId?" ++ statusQueryString ++ fromDateQueryString ++ toDateQueryString, validHeadersJson))
      status(result) shouldBe expectedStatusCode

      expectedStatusCode match {
        case NOT_FOUND => verifyNoInteractions(mockNotificationService)
        case OK => verify(mockNotificationService).getNotifications(
          eqTo(topicId),
          eqTo(clientId),
          eqTo(maybeStatus),
          eqTo(maybeFromDate),
          eqTo(maybeToDate))(any[ExecutionContext])
      }
    }

    def stringToDateTime(dateStr: String): DateTime = {
      DateTime.parse(dateStr)
    }

    def stringToDateTimeLenient(dateStr: Option[String]): Option[DateTime] = {
      Try[Option[DateTime]] {
        dateStr.map(DateTime.parse)
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

    private def doPOSTorPUT(uri: String, headers: Map[String, String], bodyValue: String, method: String): Future[Result]

    =
    {
      val maybeBody: Option[JsValue] = Try {
        Json.parse(bodyValue)
      } match {
        case Success(value) => Some(value)
        case Failure(_) => None
      }

      val fakeRequest = FakeRequest(method, uri).withHeaders(headers.toSeq: _*)
      maybeBody
        .fold(route(app, fakeRequest.withBody(bodyValue)).get)(jsonBody => route(app, fakeRequest.withJsonBody(jsonBody)).get)

    }
  }

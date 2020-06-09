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
import org.mockito.ArgumentMatchersSugar
import org.mockito.Mockito.{reset, verify, verifyNoInteractions, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers.{BAD_REQUEST, POST, route, _}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.{AuthConnector, SessionRecordNotFound}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.{RECEIVED, UNKNOWN}
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationContentType, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationsService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class NotificationsControllerSpec extends UnitSpec with MockitoSugar with ArgumentMatchersSugar
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

  val clientIdStr: String = UUID.randomUUID().toString
  val clientId: ClientId = ClientId(clientIdStr)
  val incorrectClientId: String = "badclientid"
  val topicName: String = "topicName"
  val topicIdStr: String = UUID.randomUUID().toString
  val topicId: TopicId = TopicId(UUID.fromString(topicIdStr))
  val jsonBody: String = "{}"
  val xmlBody: String = "<someNode/>"

  private val validHeadersJson: Map[String, String] = Map("Content-Type" -> "application/json", "X-CLIENT-ID" -> clientId.value)
  private val validHeadersXml: Map[String, String] = Map("Content-Type" -> "application/xml", "X-CLIENT-ID" -> clientId.value)

  val createdDateTime: DateTime = DateTime.now().minusDays(1)
  val notification: Notification = Notification(NotificationId(UUID.randomUUID()), topicId,
    notificationContentType = NotificationContentType.APPLICATION_JSON,
    message = "{}",
    createdDateTime = createdDateTime,
    status = RECEIVED)

  val notification2: Notification = Notification(NotificationId(UUID.randomUUID()), topicId,
    notificationContentType = NotificationContentType.APPLICATION_XML,
    message = "<someXml/>",
    createdDateTime = createdDateTime.plusHours(12),
    status = NotificationStatus.READ)


  "NotificationController" when {
    "saveNotification" should {
      "return 201 when valid json, json content type header are provided and notification successfully saved" in {
        when(mockNotificationService.saveNotification(eqTo(topicId),
          any[NotificationId],
          eqTo(NotificationContentType.APPLICATION_JSON),
          eqTo(jsonBody))(any[ExecutionContext])).thenReturn(Future.successful(Right(SaveNotificationSuccessResult())))

        val result = doPost(s"/notifications/topics/${topicId.raw}", validHeadersJson, jsonBody)
        status(result) should be(CREATED)

        verify(mockNotificationService)
          .saveNotification(eqTo(topicId), any[NotificationId], eqTo(NotificationContentType.APPLICATION_JSON), eqTo(jsonBody))(any[ExecutionContext])
      }

      "return 201 when valid xml, xml content type header are provided and notification successfully saved" in {
        when(mockNotificationService
          .saveNotification(eqTo(topicId), any[NotificationId], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.successful(Right(SaveNotificationSuccessResult())))

        val result = doPost(s"/notifications/topics/${topicId.raw}", validHeadersXml, xmlBody)
        status(result) should be(CREATED)

        verify(mockNotificationService)
          .saveNotification(eqTo(topicId), any[NotificationId], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }

      "return 400 when json content type header is sent but invalid json" in {

        val result = doPost(s"/notifications/topics/${topicId.raw}", validHeadersJson, xmlBody)
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 400 when xml content type header is sent but invalid xml" in {

        val result = doPost(s"/notifications/topics/${topicId.raw}", validHeadersXml, jsonBody)
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 400 when no contentType header is sent" in {

        val result = doPost(s"/notifications/topics/${topicId.raw}", Map.empty, "jsonBody")
        status(result) should be(BAD_REQUEST)

        verifyNoInteractions(mockNotificationService)
      }

      "return 500 when save notification throws Duplicate Notification Exception" in {

        when(mockNotificationService
          .saveNotification(eqTo(topicId), any[NotificationId], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.successful(Left(SaveNotificationFailedDuplicateNotificationResult("error"))))


        val result = await(doPost(s"/notifications/topics/${topicId.raw}", validHeadersXml, xmlBody))
        status(result) should be(INTERNAL_SERVER_ERROR)
        val bodyVal = Helpers.contentAsString(result)
        bodyVal shouldBe "{\"code\":\"DUPLICATE_NOTIFICATION\",\"message\":\"Unable to save Notification: duplicate found\"}"


        verify(mockNotificationService)
          .saveNotification(eqTo(topicId), any[NotificationId], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }

      "return 404 when save notification throws Topic not found Exception" in {

        when(mockNotificationService
          .saveNotification(eqTo(topicId), any[NotificationId], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.successful(Left(NotificationsServiceTopicNotFoundResult("some Exception"))))

        val result = doPost(s"/notifications/topics/${topicId.raw}", validHeadersXml, xmlBody)
        status(result) should be(NOT_FOUND)

        verify(mockNotificationService)
          .saveNotification(eqTo(topicId), any[NotificationId], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }

      "return 500 when save notification throws Any non handled Non fatal exception" in {

        when(mockNotificationService
          .saveNotification(eqTo(topicId), any[NotificationId], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.failed(new RuntimeException("some Exception")))

        val result = doPost(s"/notifications/topics/${topicId.raw}", validHeadersXml, xmlBody)
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockNotificationService)
          .saveNotification(eqTo(topicId), any[NotificationId], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }
    }

    "getNotificationsByTopicIdAndFilters" should {

      "return 200 and list of matching notifications when no filters provided" in {
        testAndValidateGetByQueryParams(topicId, OK, None)
      }

      "return 200 and list of matching notifications when status filter provided" in {
        testAndValidateGetByQueryParams(topicId, OK, Some("READ"))
      }

      "return 400 when invalid status filter provided" in {
        testAndValidateGetByQueryParams(topicId, BAD_REQUEST, Some("KBUO"))
      }

      "return 200 when valid from_date filter provided" in {
        testAndValidateGetByQueryParams(topicId, OK, None, maybeFromDateStr = Some("2020-02-02T00:54:00Z"))
      }

      "return 200 when valid status, from_date filter are provided" in {
        testAndValidateGetByQueryParams(topicId, OK, Some("RECEIVED"), maybeFromDateStr = Some("2020-02-02T00:54:00Z"))
      }

      "return 400 when invalid from_date filter provided" in {
        testAndValidateGetByQueryParams(topicId, BAD_REQUEST, None, maybeFromDateStr = Some("4433:33:88T223322"))
      }

      "return 200 when valid to_date filter provided" in {
        testAndValidateGetByQueryParams(topicId, OK, None, maybeToDateStr = Some("2020-02-02T00:54:00Z"))
      }

      "return 200 when valid to_date and status filters are provided" in {
        testAndValidateGetByQueryParams(topicId, OK, Some("RECEIVED"), maybeToDateStr = Some("2020-02-02T00:54:00Z"))
      }

      "return 400 when invalid to_date filter provided" in {
        testAndValidateGetByQueryParams(topicId, BAD_REQUEST, None, maybeToDateStr = Some("4433:33:88T223322"))

      }

      "return 401 when clientId does not match" in {
        val fromdatStr = "2020-02-02T00:54:00Z"
        val toDateStr = "2020-02-02T00:54:00Z"
        primeAuthAction(UUID.randomUUID().toString)
        when(mockNotificationService.getNotifications(
          eqTo(topicId),
          any[ClientId],
          eqTo(Some(RECEIVED)),
          eqTo(stringToDateTimeLenient(Some(fromdatStr))),
          eqTo(stringToDateTimeLenient(Some(toDateStr))))(any[ExecutionContext]))
          .thenReturn(Future.successful(Left(NotificationsServiceUnauthorisedResult(""))))

        val result = await(doGet(s"/notifications/topics/${topicId.raw}?status=RECEIVED&from_date=$fromdatStr&to_date=$toDateStr", validHeadersJson))
        status(result) shouldBe UNAUTHORIZED
      }

      "return 200 with empty List when no notifictions returned" in {
        val fromdatStr = "2020-02-02T00:54:00Z"
        val toDateStr = "2020-02-02T00:54:00Z"
        primeAuthAction(clientIdStr)
        when(mockNotificationService.getNotifications(
          eqTo(topicId),
          eqTo(clientId),
          eqTo(Some(RECEIVED)),
          eqTo(stringToDateTimeLenient(Some(fromdatStr))),
          eqTo(stringToDateTimeLenient(Some(toDateStr))))(any[ExecutionContext]))
          .thenReturn(Future.successful(Right(GetNotificationsSuccessRetrievedResult(List.empty))))

        val result = await(doGet(s"/notifications/topics/${topicId.raw}?status=RECEIVED&from_date=$fromdatStr&to_date=$toDateStr", validHeadersJson))
        status(result) shouldBe OK
      }

      "return 401 when no clientId is returned from auth" in {
        when(mockAuthConnector.authorise[Option[String]](any[Predicate], any[Retrieval[Option[String]]])(any[HeaderCarrier], any[concurrent.ExecutionContext]))
          .thenReturn(Future.successful(None))

        val result = await(doGet(s"/notifications/topics/${topicId.raw}", validHeadersJson))
        status(result) shouldBe UNAUTHORIZED
      }

      "return 401 when authorisation fails" in {
        when(mockAuthConnector.authorise[Option[String]](any[Predicate], any[Retrieval[Option[String]]])(any[HeaderCarrier], any[concurrent.ExecutionContext]))
          .thenReturn(Future.failed(SessionRecordNotFound()))

        val result = await(doGet(s"/notifications/topics/${topicId.raw}", validHeadersJson))
        status(result) shouldBe UNAUTHORIZED
      }
    }
  }

  private def primeAuthAction(clientId: String): Unit = {
    when(mockAuthConnector.authorise[Option[String]](any[Predicate], any[Retrieval[Option[String]]])(any[HeaderCarrier], any[concurrent.ExecutionContext])).thenReturn(Future.successful(Some(clientId)))

  }

  private def testAndValidateGetByQueryParams(topicId: TopicId,
                                              expectedStatusCode: Int,
                                              maybeNotificationStatus: Option[String],
                                              maybeFromDateStr: Option[String] = None,
                                              maybeToDateStr: Option[String] = None): Unit = {
    if (expectedStatusCode.equals(UNAUTHORIZED)) {
      when(mockAuthConnector.authorise[Option[String]](any[Predicate], any[Retrieval[Option[String]]])
        (any[HeaderCarrier], any[concurrent.ExecutionContext])).thenReturn(Future.successful(None))
    } else {
      primeAuthAction(clientIdStr)
    }
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
        eqTo(maybeToDate))(any[ExecutionContext]))
        .thenReturn(Future.successful(Right(GetNotificationsSuccessRetrievedResult(List(notification, notification2)))))
      case NOT_FOUND => ()
      case BAD_REQUEST => ()
    }

    val statusQueryString = maybeNotificationStatus.fold("")(x => s"status=$x&")
    val toDateQueryString = maybeToDateStr.fold("")(x => s"to_date=$x&")
    val fromDateQueryString = maybeFromDateStr.fold("")(x => s"from_date=$x&")

    val result = await(doGet(s"/notifications/topics/${topicId.raw}?" ++ statusQueryString ++ fromDateQueryString ++ toDateQueryString, validHeadersJson))
    status(result) shouldBe expectedStatusCode

    expectedStatusCode match {
      case NOT_FOUND => verifyNoInteractions(mockNotificationService)
      case BAD_REQUEST => verifyNoInteractions(mockNotificationService)
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

  = {
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

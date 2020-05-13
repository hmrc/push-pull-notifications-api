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
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationContentType
import uk.gov.hmrc.pushpullnotificationsapi.models.{DuplicateNotificationException, TopicNotFoundException}
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationsService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class NotificationsControllerSpec extends UnitSpec with MockitoSugar
  with GuiceOneAppPerSuite with BeforeAndAfterEach {

  val mockNotificationService: NotificationsService = mock[NotificationsService]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[NotificationsService].to(mockNotificationService))
    .build()

  override def beforeEach(): Unit = {
    reset(mockNotificationService)
  }

  val clientId: String = "clientid"
  val topicName: String = "topicName"
  val topicId: String = UUID.randomUUID().toString
  val jsonBody: String = "{}"
  val xmlBody: String = "<someNode/>"

  private val validHeadersJson: Map[String, String] = Map("Content-Type" -> "application/json")
  private val validHeadersXml: Map[String, String] = Map("Content-Type" -> "application/xml")


  "NotificationController" when {
    "saveNotification" should {
      "return 201 when valid json, json content type header are provided and notification successfully saved" in {
        when(mockNotificationService.saveNotification(eqTo(topicId),
          any[UUID],
          eqTo(NotificationContentType.APPLICATION_JSON),
          eqTo(jsonBody))(any[ExecutionContext])).thenReturn(Future.successful(true))

        val result = doPost(s"/notifications/topics/$topicId", validHeadersJson, jsonBody)
        status(result) should be(CREATED)

        verify(mockNotificationService).saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_JSON), eqTo(jsonBody))(any[ExecutionContext])
      }

      "return 201 when valid xml, xml content type header are provided and notification successfully saved" in {
        when(mockNotificationService.saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.successful(true))

        val result = doPost(s"/notifications/topics/$topicId", validHeadersXml, xmlBody)
        status(result) should be(CREATED)

        verify(mockNotificationService).saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
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

        when(mockNotificationService.saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.successful(false))

        val result = doPost(s"/notifications/topics/$topicId", validHeadersXml, xmlBody)
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockNotificationService).saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }

      "return 422 when save notification throws Duplicate Notification Exception" in {

        when(mockNotificationService.saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.failed(DuplicateNotificationException("some Exception")))

        val result = await(doPost(s"/notifications/topics/$topicId", validHeadersXml, xmlBody))
        status(result) should be(UNPROCESSABLE_ENTITY)


        verify(mockNotificationService).saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }

      "return 404 when save notification throws Topic not found Exception" in {

        when(mockNotificationService.saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.failed(TopicNotFoundException("some Exception")))

        val result = doPost(s"/notifications/topics/$topicId", validHeadersXml, xmlBody)
        status(result) should be(NOT_FOUND)

        verify(mockNotificationService).saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }

      "return 500 when save notification throws Any non handled Non fatal exception" in {

        when(mockNotificationService.saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext]))
          .thenReturn(Future.failed(new RuntimeException("some Exception")))

        val result = doPost(s"/notifications/topics/$topicId", validHeadersXml, xmlBody)
        status(result) should be(INTERNAL_SERVER_ERROR)

        verify(mockNotificationService).saveNotification(eqTo(topicId), any[UUID], eqTo(NotificationContentType.APPLICATION_XML), eqTo(xmlBody))(any[ExecutionContext])
      }
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
      case Failure(_) => None
    }

    val fakeRequest = FakeRequest(method, uri).withHeaders(headers.toSeq: _*)
    maybeBody
      .fold(route(app, fakeRequest.withBody(bodyValue)).get)(jsonBody => route(app, fakeRequest.withJsonBody(jsonBody)).get)

  }
}

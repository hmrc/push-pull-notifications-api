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

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util._
import scala.xml.NodeSeq

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.mvc.Results.{InternalServerError, NotFound}
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, NotificationId}
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationsService

trait NotificationUtils {
  implicit val ec: ExecutionContext
  def notificationsService: NotificationsService

  protected def contentTypeHeaderToNotificationType(contentType: String): Option[MessageContentType] = {
    contentType match {
      case MimeTypes.JSON => Some(MessageContentType.APPLICATION_JSON)
      case MimeTypes.XML  => Some(MessageContentType.APPLICATION_XML)
      case _              => None
    }
  }

  protected def processNotification(
      boxId: BoxId,
      contentType: MessageContentType,
      message: String
    )(fn: (NotificationId) => Future[Result]
    )(implicit hc: HeaderCarrier
    ): Future[Result] = {
    val notificationId = NotificationId.random

    notificationsService.saveNotification(boxId, notificationId, contentType, message) flatMap {
      case _: NotificationCreateSuccessResult             => fn(notificationId)
      case _: NotificationCreateFailedBoxIdNotFoundResult => successful(NotFound(JsErrorResponse(ErrorCode.BOX_NOT_FOUND, "Box not found")))
      case _: NotificationCreateFailedDuplicateResult     =>
        successful(InternalServerError(JsErrorResponse(ErrorCode.DUPLICATE_NOTIFICATION, "Unable to save Notification: duplicate found")))
    } recover recovery
  }

  protected def validateBodyAgainstContentType(notificationContentType: MessageContentType, body: String): Boolean = {
    def validateBody[T](body: String, f: String => T): Boolean = {
      Try[T] {
        f(body)
      } match {
        case Success(_) => true
        case Failure(_) => false
      }
    }
    def checkValidJson(body: String) = validateBody[JsValue](body, body => Json.parse(body))
    def checkValidXml(body: String) = validateBody[NodeSeq](body, body => scala.xml.XML.loadString(body))

    notificationContentType match {
      case MessageContentType.APPLICATION_JSON => checkValidJson(body)
      case MessageContentType.APPLICATION_XML  => checkValidXml(body)
    }
  }
}

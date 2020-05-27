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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders.{ValidateNotificationQueryParamsAction, ValidatedNotificationHeadersAction}
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationContentType}
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationContentType._
import uk.gov.hmrc.pushpullnotificationsapi.models.{DuplicateNotificationException, ErrorCode, JsErrorResponse, TopicNotFoundException}
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationsService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

@Singleton()
class NotificationsController @Inject()(appConfig: AppConfig,
                                        notificationsService: NotificationsService,
                                        queryParamValidatorAction: ValidateNotificationQueryParamsAction,
                                        headerValidatorAction: ValidatedNotificationHeadersAction,
                                        cc: ControllerComponents,
                                        playBodyParsers: PlayBodyParsers)
                                       (implicit val ec: ExecutionContext) extends BackendController(cc) {

  def saveNotification(topicId: String): Action[String] = Action.async(playBodyParsers.tolerantText) { implicit request =>
    val convertedType = contentTypeHeaderToNotificationType
    if (validateContentTypeAndBody(convertedType)) {
      val notificationId = UUID.randomUUID()
      notificationsService.saveNotification(topicId, notificationId, convertedType, request.body) map {
        case true => Created(notificationId.toString)
        case false => InternalServerError(JsErrorResponse(ErrorCode.UNKNOWN_ERROR, "Unable to save Notification: Unknown Error"))
      } recover recovery

    } else {
      Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Content Type not Supported or message syntax is invalid")))
    }
  }

  def getNotificationsByTopicIdAndFilters(topicId: String): Action[AnyContent] =
    (Action andThen
      queryParamValidatorAction andThen
      headerValidatorAction)
    .async {implicit request =>
      notificationsService.getNotifications(topicId, request.clientId, request.params.status, request.params.fromDate, request.params.toDate) map {
        case Nil => Ok("no results")
        case x : List[Notification] =>  Ok(Json.toJson(x))
      } recover recovery
    }

  private def contentTypeHeaderToNotificationType()(implicit request: Request[String]): NotificationContentType = {
    request.contentType match {
      case Some(MimeTypes.JSON) => APPLICATION_JSON
      case Some(MimeTypes.XML) => APPLICATION_XML
      case _ => UNSUPPORTED
    }
  }


  private def validateContentTypeAndBody(notificationContentType: NotificationContentType)(implicit request: Request[String]) = {
    notificationContentType match {
      case APPLICATION_JSON => checkValidJson(request.body)
      case APPLICATION_XML => checkValidXml(request.body)
      case _ => false
    }
  }

  private def checkValidJson(body: String) = {
    validateBody[JsValue](body, body => Json.parse(body))
  }

  private def checkValidXml(body: String) = {
    validateBody[NodeSeq](body, body =>  scala.xml.XML.loadString(body))
  }

  private def validateBody[T](body:String, f: String => T): Boolean ={
    Try[T] {
      f(body)
    } match {
      case Success(_) => true
      case Failure(_) => false
    }
  }

  private def recovery: PartialFunction[Throwable, Result] = {
    case NonFatal(e) =>
      Logger.info("An unexpected error occurred:", e)
      e match {
        case error: DuplicateNotificationException => UnprocessableEntity(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, error.message))
        case error: TopicNotFoundException => NotFound(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, error.message))
        case _ => InternalServerError(JsErrorResponse(ErrorCode.UNKNOWN_ERROR, e.getMessage))
      }

  }

}

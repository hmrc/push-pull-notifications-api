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
import uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders.{AuthAction, ValidateNotificationQueryParamsAction}
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationContentType, NotificationId}
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationContentType._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationsService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

@Singleton()
class NotificationsController @Inject()(appConfig: AppConfig,
                                        notificationsService: NotificationsService,
                                        queryParamValidatorAction: ValidateNotificationQueryParamsAction,
                                        authAction: AuthAction,
                                        cc: ControllerComponents,
                                        playBodyParsers: PlayBodyParsers)(implicit val ec: ExecutionContext)
  extends BackendController(cc)  {

  def saveNotification(topicId: TopicId): Action[String] = Action.async(playBodyParsers.tolerantText) { implicit request =>
    val convertedType = contentTypeHeaderToNotificationType
    if (validateContentTypeAndBody(convertedType)) {
      val notificationId = NotificationId(UUID.randomUUID())
      notificationsService.saveNotification(topicId, notificationId, convertedType, request.body) map {
        case Right(_: SaveNotificationSuccessResult) => Created(notificationId.toString)
        case Left(_: NotificationsServiceTopicNotFoundResult) =>
          NotFound(JsErrorResponse(ErrorCode.TOPIC_NOT_FOUND, "Unable to save Notification: topicId not found"))
        case Left(_: SaveNotificationFailedDuplicateNotificationResult)  =>
          UnprocessableEntity(JsErrorResponse(ErrorCode.DUPLICATE_NOTIFICATION, "Unable to save Notification: duplicate found"))
      } recover recovery
    } else {
      Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Content Type not Supported or message syntax is invalid")))
    }
  }

  def getNotificationsByTopicIdAndFilters(topicId: TopicId): Action[AnyContent] =
    (Action andThen
            authAction andThen
      queryParamValidatorAction)
      .async { implicit request =>
            notificationsService.getNotifications(topicId, request.clientId, request.params.status, request.params.fromDate, request.params.toDate) map {
              case Right(results: GetNotificationsSuccessRetrievedResult) => Ok(Json.toJson(results.notifications))
              case Left(_: NotificationsServiceUnauthorisedResult) =>
                Unauthorized(JsErrorResponse(ErrorCode.UNAUTHORISED, "Unable to view notification for topic not created by yourself"))
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
    validateBody[NodeSeq](body, body => scala.xml.XML.loadString(body))
  }

  private def validateBody[T](body: String, f: String => T): Boolean = {
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
        case _ => InternalServerError(JsErrorResponse(ErrorCode.UNKNOWN_ERROR, e.getMessage))
      }

  }

}

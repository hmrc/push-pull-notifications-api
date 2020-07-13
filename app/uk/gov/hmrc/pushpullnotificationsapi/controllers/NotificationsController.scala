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
import java.util.regex.Pattern

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders.{AuthAction, ValidateAcceptHeaderAction, ValidateNotificationQueryParamsAction, ValidateUserAgentHeaderAction}
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.RequestFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.MessageContentType._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, NotificationId}
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationsService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

@Singleton()
class NotificationsController @Inject()(notificationsService: NotificationsService,
                                        queryParamValidatorAction: ValidateNotificationQueryParamsAction,
                                        validateUserAgentHeaderAction: ValidateUserAgentHeaderAction,
                                        authAction: AuthAction,
                                        validateAcceptHeaderAction: ValidateAcceptHeaderAction,
                                        cc: ControllerComponents,
                                        playBodyParsers: PlayBodyParsers)(implicit val ec: ExecutionContext)
  extends BackendController(cc) {

  private val notificationLimit = 100

  def saveNotification(boxId: BoxId): Action[String] =
    (Action andThen
      validateUserAgentHeaderAction)
      .async(playBodyParsers.tolerantText) { implicit request =>
        val maybeConvertedType = contentTypeHeaderToNotificationType
        maybeConvertedType.fold(
          Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Content Type not Supported or message syntax is invalid")))
        ) { contentType =>
          if (validateBodyAgainstContentType(contentType)) {
            val notificationId = NotificationId(UUID.randomUUID())
            notificationsService.saveNotification(boxId, notificationId, contentType, request.body) map {
              case _: NotificationCreateSuccessResult => Created(Json.toJson(CreateNotificationResponse(notificationId.raw)))
              case _: NotificationCreateFailedBoxIdNotFoundResult =>
                NotFound(JsErrorResponse(ErrorCode.BOX_NOT_FOUND, "Box not found"))
              case _: NotificationCreateFailedDuplicateResult =>
                InternalServerError(JsErrorResponse(ErrorCode.DUPLICATE_NOTIFICATION, "Unable to save Notification: duplicate found"))
            } recover recovery
          } else {
            Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Content Type not Supported or message syntax is invalid")))
          }
        }
      }

  def getNotificationsByBoxIdAndFilters(boxId: BoxId): Action[AnyContent] =
    (Action andThen
      validateAcceptHeaderAction andThen
      authAction andThen
      queryParamValidatorAction)
      .async { implicit request =>
        notificationsService.getNotifications(boxId, request.clientId, request.params.status, request.params.fromDate, request.params.toDate) map {
          case results: GetNotificationsSuccessRetrievedResult => Ok(Json.toJson(results.notifications))
          case _: GetNotificationsServiceBoxNotFoundResult =>
            NotFound(JsErrorResponse(ErrorCode.BOX_NOT_FOUND, "Box not found"))
          case _: GetNotificationsServiceUnauthorisedResult =>
            Forbidden(JsErrorResponse(ErrorCode.FORBIDDEN, "Access denied"))
        } recover recovery
      }

  val UUIDRegex: Regex = raw"\b[0-9a-f]{8}\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\b[0-9a-f]{12}\b".r

  def validateAcknowledgeRequest(request: AcknowledgeNotificationsRequest): Boolean = {
    //duplicates?

    if (request.notificationIds.isEmpty || request.notificationIds.size > notificationLimit) {
      false
    } else {
      val uniqueIds = request.notificationIds.distinct
      request.notificationIds.exists(UUIDRegex.findFirstIn(_).isDefined) && uniqueIds.equals(request.notificationIds)
    }

  }

  def acknowledgeNotifications(boxId: BoxId): Action[JsValue] = Action.async(playBodyParsers.json) {
    implicit request =>
      withJsonBody[AcknowledgeNotificationsRequest] {
        jsonValue =>
          if (validateAcknowledgeRequest(jsonValue)) {
            Future.successful(Ok(""))
          } else {
            Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "JSON body is invalid against expected format")))
          }
      } recover recovery
  }

  private def contentTypeHeaderToNotificationType()(implicit request: Request[String]): Option[MessageContentType] = {
    request.contentType match {
      case Some(MimeTypes.JSON) => Some(APPLICATION_JSON)
      case Some(MimeTypes.XML) => Some(APPLICATION_XML)
      case _ => None
    }
  }


  private def validateBodyAgainstContentType(notificationContentType: MessageContentType)(implicit request: Request[String]) = {
    notificationContentType match {
      case APPLICATION_JSON => checkValidJson(request.body)
      case APPLICATION_XML => checkValidXml(request.body)
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

  override protected def withJsonBody[T]
  (f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result]
  = {
    withJson(request.body)(f)
  }

  private def withJson[T](json: JsValue)(f: T => Future[Result])(implicit reads: Reads[T]): Future[Result] = {
    json.validate[T] match {
      case JsSuccess(payload, _) => f(payload)
      case JsError(errs) =>
        Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "JSON body is invalid against expected format")))
    }
  }

  private def recovery: PartialFunction[Throwable, Result] = {
    case NonFatal(e) =>
      Logger.error("An unexpected error occurred:", e)
      InternalServerError(JsErrorResponse(ErrorCode.UNKNOWN_ERROR, e.getMessage))
  }

}

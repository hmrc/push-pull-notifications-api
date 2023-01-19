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

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

import play.api.libs.json._
import play.api.mvc._
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders.{AuthAction, ValidateAcceptHeaderAction, ValidateNotificationQueryParamsAction, ValidateUserAgentHeaderAction}
import uk.gov.hmrc.pushpullnotificationsapi.models.NotificationResponse.fromNotification
import uk.gov.hmrc.pushpullnotificationsapi.models.RequestFormatters.wrappedNotificationRequestFormatter
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.MessageContentType._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, NotificationId}
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationsService

@Singleton()
class NotificationsController @Inject() (
    appConfig: AppConfig,
    notificationsService: NotificationsService,
    queryParamValidatorAction: ValidateNotificationQueryParamsAction,
    validateUserAgentHeaderAction: ValidateUserAgentHeaderAction,
    authAction: AuthAction,
    validateAcceptHeaderAction: ValidateAcceptHeaderAction,
    cc: ControllerComponents,
    playBodyParsers: PlayBodyParsers
  )(implicit val ec: ExecutionContext)
    extends BackendController(cc) {

  def saveNotification(boxId: BoxId): Action[String] =
    (Action andThen
      validateUserAgentHeaderAction)
      .async(playBodyParsers.tolerantText(appConfig.maxNotificationSize)) { implicit request =>
        val maybeConvertedType = contentTypeHeaderToNotificationType
        maybeConvertedType.fold(
          Future.successful(UnsupportedMediaType(JsErrorResponse(ErrorCode.BAD_REQUEST, "Content Type not Supported")))
        ) { contentType =>
          if (validateBodyAgainstContentType(contentType)) {
            val notificationId = NotificationId(UUID.randomUUID())
            notificationsService.saveNotification(boxId, notificationId, contentType, request.body) map {
              case _: NotificationCreateSuccessResult             =>
                Created(Json.toJson(CreateNotificationResponse(notificationId.raw)))
              case _: NotificationCreateFailedBoxIdNotFoundResult =>
                NotFound(JsErrorResponse(ErrorCode.BOX_NOT_FOUND, "Box not found"))
              case _: NotificationCreateFailedDuplicateResult     =>
                InternalServerError(JsErrorResponse(ErrorCode.DUPLICATE_NOTIFICATION, "Unable to save Notification: duplicate found"))
            } recover recovery
          } else {
            Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Message syntax is invalid")))
          }
        }
      }

  def saveWrappedNotification(boxId: BoxId): Action[JsValue] =
    (Action andThen
      validateUserAgentHeaderAction)
      .async(playBodyParsers.json(appConfig.maxNotificationSize + appConfig.wrappedNotificationEnvelopeSize)) {
        implicit request =>
          withJsonBody[WrappedNotificationRequest] { wrappedNotification =>
            {
              val notification = wrappedNotification.notification
              if (wrappedNotification.version.equals("1")) {
                contentTypeHeaderToNotificationType(notification.contentType).fold(
                  Future.successful(UnsupportedMediaType(JsErrorResponse(ErrorCode.BAD_REQUEST, "Content Type not Supported")))
                ) { contentType =>
                  if (validateBodyAgainstContentType(contentType, notification.body)) {
                    val notificationId = NotificationId(UUID.randomUUID())
                    notificationsService.saveNotification(boxId, notificationId, contentType, notification.body) map {
                      case _: NotificationCreateSuccessResult             =>
                        Created(Json.toJson(CreateNotificationResponse(notificationId.raw)))
                      case _: NotificationCreateFailedBoxIdNotFoundResult =>
                        NotFound(JsErrorResponse(ErrorCode.BOX_NOT_FOUND, "Box not found"))
                      case _: NotificationCreateFailedDuplicateResult     =>
                        InternalServerError(JsErrorResponse(ErrorCode.DUPLICATE_NOTIFICATION, "Unable to save Notification: duplicate found"))
                    } recover recovery
                  } else {
                    Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Message syntax is invalid")))
                  }
                }
              } else {
                Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Message version is invalid")))
              }
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
          case results: GetNotificationsSuccessRetrievedResult => Ok(Json.toJson(results.notifications.map(fromNotification)))
          case _: GetNotificationsServiceBoxNotFoundResult     =>
            NotFound(JsErrorResponse(ErrorCode.BOX_NOT_FOUND, "Box not found"))
          case _: GetNotificationsServiceUnauthorisedResult    =>
            Forbidden(JsErrorResponse(ErrorCode.FORBIDDEN, "Access denied"))
        } recover recovery
      }

  val UUIDRegex: Regex = raw"\b[0-9a-f]{8}\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\b[0-9a-f]{12}\b".r

  private def validateAcknowledgeRequest(request: AcknowledgeNotificationsRequest): Boolean = {

    val notificationIds = request.notificationIds

    if (notificationIds.isEmpty || notificationIds.size > appConfig.numberOfNotificationsToRetrievePerRequest) {
      false
    } else {
      notificationIds.count(UUIDRegex.findFirstIn(_).isDefined) == notificationIds.size &&
      notificationIds.distinct.equals(notificationIds)
    }

  }

  def acknowledgeNotifications(boxId: BoxId): Action[JsValue] =
    (Action andThen
      validateAcceptHeaderAction andThen
      authAction).async(playBodyParsers.json) { implicit request =>
      implicit val actualBody: Request[JsValue] = request.request
      withJsonBody[AcknowledgeNotificationsRequest] {
        jsonValue =>
          if (validateAcknowledgeRequest(jsonValue)) notificationsService.acknowledgeNotifications(boxId, request.clientId, jsonValue) map {
            case _: AcknowledgeNotificationsSuccessUpdatedResult      =>
              NoContent
            case _: AcknowledgeNotificationsServiceBoxNotFoundResult  =>
              NotFound(JsErrorResponse(ErrorCode.BOX_NOT_FOUND, "Box not found"))
            case _: AcknowledgeNotificationsServiceUnauthorisedResult =>
              Forbidden(JsErrorResponse(ErrorCode.FORBIDDEN, "Access denied"))
          } recover recovery
          else {
            Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "JSON body is invalid against expected format")))
          }
      }(actualBody, manifest, RequestFormatters.acknowledgeRequestFormatter)
    }

  private def contentTypeHeaderToNotificationType()(implicit request: Request[String]): Option[MessageContentType] = {
    request.contentType match {
      case Some(MimeTypes.JSON) => Some(APPLICATION_JSON)
      case Some(MimeTypes.XML)  => Some(APPLICATION_XML)
      case _                    => None
    }
  }

  private def contentTypeHeaderToNotificationType(contentType: String): Option[MessageContentType] = {
    contentType match {
      case MimeTypes.JSON => Some(APPLICATION_JSON)
      case MimeTypes.XML  => Some(APPLICATION_XML)
      case _              => None
    }
  }

  private def validateBodyAgainstContentType(notificationContentType: MessageContentType, body: String) = {
    notificationContentType match {
      case APPLICATION_JSON => checkValidJson(body)
      case APPLICATION_XML  => checkValidXml(body)
    }
  }

  private def validateBodyAgainstContentType(notificationContentType: MessageContentType)(implicit request: Request[String]) = {
    notificationContentType match {
      case APPLICATION_JSON => checkValidJson(request.body)
      case APPLICATION_XML  => checkValidXml(request.body)
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

  override protected def withJsonBody[T](f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] = {
    withJson(request.body)(f)
  }

  private def withJson[T](json: JsValue)(f: T => Future[Result])(implicit reads: Reads[T]): Future[Result] = {
    json.validate[T] match {
      case JsSuccess(payload, _) => f(payload)
      case JsError(errs)         =>
        Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "JSON body is invalid against expected format")))
    }
  }
}

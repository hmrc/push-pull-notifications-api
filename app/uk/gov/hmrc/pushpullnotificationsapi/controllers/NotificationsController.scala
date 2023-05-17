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
import scala.concurrent.Future.successful

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
import uk.gov.hmrc.pushpullnotificationsapi.services.{ConfirmationService, NotificationsService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper

@Singleton()
class NotificationsController @Inject() (
    appConfig: AppConfig,
    notificationsService: NotificationsService,
    confirmationService: ConfirmationService,
    queryParamValidatorAction: ValidateNotificationQueryParamsAction,
    validateUserAgentHeaderAction: ValidateUserAgentHeaderAction,
    authAction: AuthAction,
    validateAcceptHeaderAction: ValidateAcceptHeaderAction,
    cc: ControllerComponents,
    playBodyParsers: PlayBodyParsers
  )(implicit val ec: ExecutionContext)
    extends BackendController(cc) {

  val ET = EitherTHelper.make[Result]

  val maxNotificationSize = appConfig.maxNotificationSize
  val maxWrappedNotificationSize = maxNotificationSize + appConfig.wrappedNotificationEnvelopeSize
  
  def saveNotification(boxId: BoxId): Action[String] =
    (Action andThen validateUserAgentHeaderAction).async(playBodyParsers.tolerantText(maxNotificationSize)) { implicit request =>
      
      val handleNotification = (notificationId: NotificationId) => successful(Created(Json.toJson(CreateNotificationResponse(notificationId.raw))))

      (
        for {
          contentType        <- ET.fromOption(request.contentType, UnsupportedMediaType(JsErrorResponse(ErrorCode.BAD_REQUEST, "Content Type not found")) )
          messageContentType <- ET.fromOption(contentTypeHeaderToNotificationType(contentType), UnsupportedMediaType(JsErrorResponse(ErrorCode.BAD_REQUEST, "Content Type not Supported")) )
          body                = request.body
          isValidBody         = validateBodyAgainstContentType(messageContentType, body)
          messageBody        <- ET.cond(isValidBody, body, BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Message syntax is invalid")))
          result             <- ET.liftF(processNotification(boxId, messageContentType, messageBody)(handleNotification))
        }
        yield result
      ).merge
    }
      
  def saveWrappedNotification(boxId: BoxId): Action[JsValue] =
    (Action andThen validateUserAgentHeaderAction).async(playBodyParsers.json(maxWrappedNotificationSize)) {
      implicit request =>
        withJsonBody[WrappedNotificationRequest] { wrappedNotification =>

        val handleNotification = (notificationId: NotificationId) => {
          wrappedNotification.confirmationUrl.fold(successful(Created(Json.toJson(CreateNotificationResponse(notificationId.raw))))){
          confirmationUrl =>     
            val confirmationId = ConfirmationId(UUID.randomUUID())
            confirmationService.saveConfirmationRequest(confirmationId, confirmationUrl, notificationId) map {
              case _: ConfirmationCreateServiceSuccessResult =>
                Created(Json.toJson(CreateWrappedNotificationResponse(notificationId.raw, confirmationId.raw)))
              case _: ConfirmationCreateServiceFailedResult  =>
                InternalServerError(JsErrorResponse(ErrorCode.DUPLICATE_CONFIRMATION, "Unable to save Confirmation: duplicate found"))
            }
          }
        }
        
        (
          for {
            _                  <- ET.cond(wrappedNotification.version == "1", (), BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Message version is invalid")))
            messageContentType <- ET.fromOption(contentTypeHeaderToNotificationType(wrappedNotification.notification.contentType), UnsupportedMediaType(JsErrorResponse(ErrorCode.BAD_REQUEST, "Content Type not Supported")) )
            body                = wrappedNotification.notification.body
            isValidBody         = validateBodyAgainstContentType(messageContentType, body)
            messageBody        <- ET.cond(isValidBody, body, BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Message syntax is invalid")))
            result             <- ET.liftF(processNotification(boxId, messageContentType, messageBody)(handleNotification))
          }
          yield result
        ).merge
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

  private def contentTypeHeaderToNotificationType(contentType: String): Option[MessageContentType] = {
    contentType match {
      case MimeTypes.JSON => Some(APPLICATION_JSON)
      case MimeTypes.XML  => Some(APPLICATION_XML)
      case _              => None
    }
  }

  private def processNotification(boxId: BoxId, contentType: MessageContentType, message: String)(fn: (NotificationId) => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    val notificationId = NotificationId(UUID.randomUUID())

    notificationsService.saveNotification(boxId, notificationId, contentType, message) flatMap {
      case _: NotificationCreateSuccessResult             => fn(notificationId)    
      case _: NotificationCreateFailedBoxIdNotFoundResult => successful(NotFound(JsErrorResponse(ErrorCode.BOX_NOT_FOUND, "Box not found")))
      case _: NotificationCreateFailedDuplicateResult     => successful(InternalServerError(JsErrorResponse(ErrorCode.DUPLICATE_NOTIFICATION, "Unable to save Notification: duplicate found")))
    } recover recovery
  }

  private def validateBodyAgainstContentType(notificationContentType: MessageContentType, body: String): Boolean = {
    notificationContentType match {
      case APPLICATION_JSON => checkValidJson(body)
      case APPLICATION_XML  => checkValidXml(body)
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

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

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders.{AuthAction, ValidateAcceptHeaderAction, ValidateNotificationQueryParamsAction, ValidateUserAgentHeaderAction}
import uk.gov.hmrc.pushpullnotificationsapi.models.NotificationResponse.fromNotification
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationId}
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationsService

@Singleton()
class NotificationsController @Inject() (
    appConfig: AppConfig,
    val notificationsService: NotificationsService,
    queryParamValidatorAction: ValidateNotificationQueryParamsAction,
    validateUserAgentHeaderAction: ValidateUserAgentHeaderAction,
    authAction: AuthAction,
    validateAcceptHeaderAction: ValidateAcceptHeaderAction,
    cc: ControllerComponents,
    playBodyParsers: PlayBodyParsers
  )(implicit val ec: ExecutionContext)
    extends BackendController(cc)
    with NotificationUtils
    with WithJsonBodyWithBadRequest {

  val ET = EitherTHelper.make[Result]

  val maxNotificationSize = appConfig.maxNotificationSize
  val maxWrappedNotificationSize = maxNotificationSize + appConfig.wrappedNotificationEnvelopeSize

  def saveNotification(boxId: BoxId): Action[String] =
    (Action andThen validateUserAgentHeaderAction).async(playBodyParsers.tolerantText(maxNotificationSize)) { implicit request =>
      val handleNotification = (notificationId: NotificationId) => successful(Created(Json.toJson(CreateNotificationResponse(notificationId))))

      (
        for {
          contentType <- ET.fromOption(request.contentType, UnsupportedMediaType(JsErrorResponse(ErrorCode.BAD_REQUEST, "Content Type not found")))
          messageContentType <- ET.fromOption(
                                  contentTypeHeaderToNotificationType(contentType),
                                  UnsupportedMediaType(JsErrorResponse(ErrorCode.BAD_REQUEST, "Content Type not Supported"))
                                )
          body = request.body
          isValidBody = validateBodyAgainstContentType(messageContentType, body)
          messageBody <- ET.cond(isValidBody, body, BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Message syntax is invalid")))
          result <- ET.liftF(processNotification(boxId, messageContentType, messageBody)(handleNotification))
        } yield result
      ).merge
    }

  def getNotificationsByBoxIdAndFilters(boxId: BoxId): Action[AnyContent] =
    (Action andThen
      validateAcceptHeaderAction andThen
      authAction andThen
      queryParamValidatorAction)
      .async { implicit request =>
        notificationsService.getNotifications(boxId, request.clientId, request.params.status, request.params.fromDate, request.params.toDate) map {
          case Right(results: List[Notification])                 => Ok(Json.toJson(results.map(fromNotification)))
          case Left(_: GetNotificationsServiceBoxNotFoundResult)  => NotFound(JsErrorResponse(ErrorCode.BOX_NOT_FOUND, "Box not found"))
          case Left(_: GetNotificationsServiceUnauthorisedResult) => Forbidden(JsErrorResponse(ErrorCode.FORBIDDEN, "Access denied"))
        } recover recovery
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
      }(actualBody, manifest, AcknowledgeNotificationsRequest.format)
    }

  private def validateAcknowledgeRequest(request: AcknowledgeNotificationsRequest): Boolean = {

    val notificationIds = request.notificationIds

    (notificationIds.nonEmpty && notificationIds.size <= appConfig.numberOfNotificationsToRetrievePerRequest && notificationIds.distinct == notificationIds)

  }
}

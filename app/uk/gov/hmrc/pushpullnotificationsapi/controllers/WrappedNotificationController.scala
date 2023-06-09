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
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders.ValidateUserAgentHeaderAction
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationId
import uk.gov.hmrc.pushpullnotificationsapi.services.{ConfirmationService, NotificationsService}

@Singleton()
class WrappedNotificationsController @Inject() (
    appConfig: AppConfig,
    val notificationsService: NotificationsService,
    confirmationService: ConfirmationService,
    validateUserAgentHeaderAction: ValidateUserAgentHeaderAction,
    cc: ControllerComponents,
    playBodyParsers: PlayBodyParsers
  )(implicit val ec: ExecutionContext)
    extends BackendController(cc)
    with NotificationUtils
    with WithJsonBodyWithBadRequest {

  val ET = EitherTHelper.make[Result]

  val maxNotificationSize = appConfig.maxNotificationSize
  val maxWrappedNotificationSize = maxNotificationSize + appConfig.wrappedNotificationEnvelopeSize

  def saveWrappedNotification(boxId: BoxId): Action[JsValue] =
    (Action andThen validateUserAgentHeaderAction).async(playBodyParsers.json(maxWrappedNotificationSize)) {
      implicit rawrequest =>
        withJsonBody[WrappedNotificationRequest] { request =>
          
          val handleNotification = (notificationId: NotificationId) => {
            request.confirmationUrl.fold(successful(Created(Json.toJson(CreateNotificationResponse(notificationId))))) {
              confirmationUrl =>
                val confirmationId = ConfirmationId.random
                confirmationService.saveConfirmationRequest(confirmationId, confirmationUrl, notificationId, request.privateHeaders) map {
                  case _: ConfirmationCreateServiceSuccessResult =>
                    Created(Json.toJson(CreateWrappedNotificationResponse(notificationId, confirmationId)))
                  case _: ConfirmationCreateServiceFailedResult  =>
                    InternalServerError(JsErrorResponse(ErrorCode.DUPLICATE_CONFIRMATION, "Unable to save Confirmation: duplicate found"))
                }
            }
          }

          (
            for {
              _ <- ET.cond(request.version == "1", (), BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Message version is invalid")))
              _ <- ET.cond(request.privateHeaders.length <= 5, (), BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Request contains more then 5 private headers")))
              messageContentType <- ET.fromOption(
                                      contentTypeHeaderToNotificationType(request.notification.contentType),
                                      UnsupportedMediaType(JsErrorResponse(ErrorCode.BAD_REQUEST, "Content Type not Supported"))
                                    )
              body = request.notification.body
              isValidBody = validateBodyAgainstContentType(messageContentType, body)
              messageBody <- ET.cond(isValidBody, body, BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Message syntax is invalid")))
              result <- ET.liftF(processNotification(boxId, messageContentType, messageBody)(handleNotification))
            } yield result
          ).merge
        }
    }

}

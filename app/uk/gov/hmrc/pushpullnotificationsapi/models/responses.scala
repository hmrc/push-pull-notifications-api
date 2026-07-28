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

package uk.gov.hmrc.pushpullnotificationsapi.models

import java.time.Instant

import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsObject, Json, OFormat}

import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, Notification, NotificationId, NotificationStatus}

case class CreateBoxResponse(boxId: BoxId)

object CreateBoxResponse {
  given OFormat[CreateBoxResponse] = Json.format[CreateBoxResponse]
}

case class CreateNotificationResponse(notificationId: NotificationId)

object CreateNotificationResponse {
  given OFormat[CreateNotificationResponse] = Json.format[CreateNotificationResponse]
}

case class CreateWrappedNotificationResponse(notificationId: NotificationId, confirmationId: ConfirmationId)

object CreateWrappedNotificationResponse {
  given OFormat[CreateWrappedNotificationResponse] = Json.format[CreateWrappedNotificationResponse]
}

case class UpdateCallbackUrlResponse(successful: Boolean, errorMessage: Option[String] = None)

object UpdateCallbackUrlResponse {
  given OFormat[UpdateCallbackUrlResponse] = Json.format[UpdateCallbackUrlResponse]
}

case class ValidateBoxOwnershipResponse(valid: Boolean)

object ValidateBoxOwnershipResponse {
  given OFormat[ValidateBoxOwnershipResponse] = Json.format[ValidateBoxOwnershipResponse]
}

case class NotificationResponse(
    notificationId: NotificationId,
    boxId: BoxId,
    messageContentType: MessageContentType,
    message: String,
    status: NotificationStatus,
    createdDateTime: Instant,
    readDateTime: Option[Instant],
    pushedDateTime: Option[Instant])

object NotificationResponse {
  import uk.gov.hmrc.pushpullnotificationsapi.util.PPNSInstantFormatter.instantWrites

  given OFormat[NotificationResponse] = Json.format[NotificationResponse]

  def fromNotification(notification: Notification): NotificationResponse = {

    NotificationResponse(
      notification.notificationId,
      notification.boxId,
      notification.messageContentType,
      notification.message,
      notification.status,
      notification.createdDateTime,
      notification.readDateTime,
      notification.pushedDateTime
    )
  }
}

enum ErrorCode {

  case AcceptHeaderInvalid,
    BadRequest,
    BoxNotFound,
    ClientNotFound,
    DuplicateBox,
    DuplicateNotification,
    DuplicateConfirmation,
    Forbidden,
    InvalidAcceptHeader,
    InvalidContentType,
    InvalidRequestPayload,
    NotFound,
    Unauthorised,
    UnknownError,
}

object JsErrorResponse {
  import uk.gov.hmrc.apiplatform.modules.common.domain.services.EnumJsonHelper.*

  def apply(errorCode: ErrorCode, message: JsValueWrapper): JsObject =
    Json.obj(
      "code" -> errorCode.asScreamingSnakeCase,
      "message" -> message
    )
}

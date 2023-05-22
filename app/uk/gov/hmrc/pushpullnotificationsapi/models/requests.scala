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

package uk.gov.hmrc.pushpullnotificationsapi.models

import java.time.Instant

import play.api.libs.json.Json
import play.api.mvc.{Request, WrappedRequest}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationId, NotificationStatus}

case class CreateBoxRequest(boxName: String, clientId: ClientId)

object CreateBoxRequest {
  implicit val format = Json.format[CreateBoxRequest]
}

case class CreateClientManagedBoxRequest(boxName: String)

object CreateClientManagedBoxRequest {
  implicit val format = Json.format[CreateClientManagedBoxRequest]
}

case class SubscriberRequest(callBackUrl: String, subscriberType: SubscriptionType)

object SubscriberRequest {
  implicit val format = Json.format[SubscriberRequest]
}

case class UpdateSubscriberRequest(subscriber: SubscriberRequest)

object UpdateSubscriberRequest {
  implicit val format = Json.format[UpdateSubscriberRequest]
}

case class UpdateCallbackUrlRequest(clientId: ClientId, callbackUrl: String) {

  def isInvalid(): Boolean = {
    this.clientId.value.isEmpty
  }
}

object UpdateCallbackUrlRequest {
  implicit val format = Json.format[UpdateCallbackUrlRequest]
}

case class UpdateManagedCallbackUrlRequest(callbackUrl: String)

object UpdateManagedCallbackUrlRequest {
  implicit val format = Json.format[UpdateManagedCallbackUrlRequest]
}

case class ValidateBoxOwnershipRequest(boxId: BoxId, clientId: ClientId)

object ValidateBoxOwnershipRequest {
  implicit val format = Json.format[ValidateBoxOwnershipRequest]
}

case class WrappedNotification(body: String, contentType: String)

object WrappedNotification {
  implicit val format = Json.format[WrappedNotification]
}

case class WrappedNotificationRequest(notification: WrappedNotification, version: String, confirmationUrl: Option[String])

object WrappedNotificationRequest {
  implicit val format = Json.format[WrappedNotificationRequest]
}
// Notifications

case class AcknowledgeNotificationsRequest(notificationIds: List[NotificationId])

object AcknowledgeNotificationsRequest {
  implicit val format = Json.format[AcknowledgeNotificationsRequest]
}

case class ValidatedAcknowledgeNotificationsRequest(boxId: BoxId, notificationIds: Set[NotificationId])

object ValidatedAcknowledgeNotificationsRequest {
  implicit val format = Json.format[ValidatedAcknowledgeNotificationsRequest]
}

// internal use only no need for json formats
case class ValidatedCreateBoxRequest[A](createBoxRequest: CreateBoxRequest, request: Request[A]) extends WrappedRequest[A](request)

case class AuthenticatedNotificationRequest[A](clientId: ClientId, request: Request[A])

case class NotificationQueryParams(status: Option[NotificationStatus], fromDate: Option[Instant], toDate: Option[Instant])

case class ValidatedNotificationQueryRequest[A](clientId: ClientId, params: NotificationQueryParams, request: Request[A])

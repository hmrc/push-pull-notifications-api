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

import play.api.mvc.{Request, WrappedRequest}

import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationId, NotificationStatus}
import java.util.UUID

case class CreateBoxRequest(boxName: String, clientId: String)

case class CreateClientManagedBoxRequest(boxName: String)

case class ValidatedCreateBoxRequest[A](createBoxRequest: CreateBoxRequest, request: Request[A]) extends WrappedRequest[A](request)

case class SubscriberRequest(callBackUrl: String, subscriberType: SubscriptionType)

case class UpdateSubscriberRequest(subscriber: SubscriberRequest)

case class UpdateCallbackUrlRequest(clientId: ClientId, callbackUrl: String) {

  def isInvalid(): Boolean = {
    this.clientId.value.isEmpty
  }
}

case class UpdateManagedCallbackUrlRequest(callbackUrl: String)

case class ValidateBoxOwnershipRequest(boxId: BoxId, clientId: ClientId)

case class WrappedNotification(body: String, contentType: String)

case class WrappedNotificationRequest(notification: WrappedNotification, version: String, confirmationUrl: Option[String])

// Notifications

case class AcknowledgeNotificationsRequest(notificationIds: List[NotificationId])
case class ValidatedAcknowledgeNotificationsRequest(boxId: BoxId, notificationIds: Set[NotificationId])

case class AuthenticatedNotificationRequest[A](clientId: ClientId, request: Request[A])

case class NotificationQueryParams(status: Option[NotificationStatus], fromDate: Option[Instant], toDate: Option[Instant])

case class ValidatedNotificationQueryRequest[A](clientId: ClientId, params: NotificationQueryParams, request: Request[A])

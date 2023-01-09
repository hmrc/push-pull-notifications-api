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

import org.joda.time.DateTime
import play.api.libs.json._
import uk.gov.hmrc.play.json.Union
import uk.gov.hmrc.pushpullnotificationsapi.connectors.ApiPlatformEventsConnector.{Actor, EventId, PpnsCallBackUriUpdatedEvent}
import uk.gov.hmrc.pushpullnotificationsapi.connectors.ApplicationResponse
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications._

object ResponseFormatters{
  val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
  implicit val JodaDateReads: Reads[org.joda.time.DateTime] = JodaReads.jodaDateReads(dateFormat)
  implicit val JodaDateWrites: Writes[org.joda.time.DateTime] = JodaWrites.jodaDateWrites(dateFormat)
  implicit val JodaDateTimeFormat: Format[DateTime] = Format(JodaDateReads, JodaDateWrites)
  implicit val notificationIdFormatter: Format[NotificationId] = Json.valueFormat[NotificationId]
  implicit val boxIdFormatter: Format[BoxId] = Json.valueFormat[BoxId]
  implicit val applicationIdFormatter: Format[ApplicationId] = Json.valueFormat[ApplicationId]
  implicit val clientIdFormatter: Format[ClientId] = Json.valueFormat[ClientId]
  implicit val formatBoxCreator: Format[BoxCreator] = Json.format[BoxCreator]
  implicit val pullSubscriberFormats: OFormat[PullSubscriber] = Json.format[PullSubscriber]
  implicit val pushSubscriberFormats: OFormat[PushSubscriber] = Json.format[PushSubscriber]
  implicit val formatSubscriber: Format[Subscriber] = Union.from[Subscriber]("subscriptionType")
    .and[PushSubscriber](SubscriptionType.API_PUSH_SUBSCRIBER.toString)
    .and[PullSubscriber](SubscriptionType.API_PULL_SUBSCRIBER.toString)
    .format
  implicit val boxFormats: OFormat[Box] = Json.format[Box]
  implicit val notificationFormatter: OFormat[Notification] = Json.format[Notification]
  implicit val notificationResponseFormatter: OFormat[NotificationResponse] = Json.format[NotificationResponse]
  implicit val createBoxResponseFormatter: OFormat[CreateBoxResponse] = Json.format[CreateBoxResponse]
  implicit val createNotificationResponseFormatter: OFormat[CreateNotificationResponse] = Json.format[CreateNotificationResponse]
  implicit val updateCallbackUrlResponseFormatter: OFormat[UpdateCallbackUrlResponse] = Json.format[UpdateCallbackUrlResponse]
  implicit val validateBoxOwnershipResponseFormatter: OFormat[ValidateBoxOwnershipResponse] = Json.format[ValidateBoxOwnershipResponse]
  implicit val clientSecretResponseFormatter: OFormat[ClientSecret] = Json.format[ClientSecret]
}

object RequestFormatters {
  implicit val clientIdFormatter: Format[ClientId] = Json.valueFormat[ClientId]
  implicit val boxIdFormatter: Format[BoxId] = Json.valueFormat[BoxId]
  implicit val createBoxRequestFormatter: OFormat[CreateBoxRequest] = Json.format[CreateBoxRequest]
  implicit val createClientManagedBoxRequestFormatter: OFormat[CreateClientManagedBoxRequest] = Json.format[CreateClientManagedBoxRequest]
  implicit val subscribersRequestFormatter: OFormat[SubscriberRequest] = Json.format[SubscriberRequest]
  implicit val updateSubscribersRequestFormatter: OFormat[UpdateSubscriberRequest] = Json.format[UpdateSubscriberRequest]
  implicit val acknowledgeRequestFormatter: OFormat[AcknowledgeNotificationsRequest] = Json.format[AcknowledgeNotificationsRequest]
  implicit val addCallbackUrlRequestFormatter: OFormat[UpdateCallbackUrlRequest] = Json.format[UpdateCallbackUrlRequest]
  implicit val updateManagedCallbackUrlRequestFormatter: OFormat[UpdateManagedCallbackUrlRequest] = Json.format[UpdateManagedCallbackUrlRequest]
  implicit val validateBoxOwnershipRequestFormatter: OFormat[ValidateBoxOwnershipRequest] = Json.format[ValidateBoxOwnershipRequest]
  implicit val wrappedNotificationFormatter: OFormat[WrappedNotification] = Json.format[WrappedNotification]
  implicit val wrappedNotificationRequestFormatter: OFormat[WrappedNotificationRequest] = Json.format[WrappedNotificationRequest]

}

object ConnectorFormatters {
  val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
  implicit val JodaDateReads: Reads[org.joda.time.DateTime] = JodaReads.jodaDateReads(dateFormat)
  implicit val JodaDateWrites: Writes[org.joda.time.DateTime] = JodaWrites.jodaDateWrites(dateFormat)
  implicit val JodaDateTimeFormat: Format[DateTime] = Format(JodaDateReads, JodaDateWrites)
  implicit val applicationIdFormatter: Format[ApplicationId] = Json.valueFormat[ApplicationId]
  implicit val forwardedHeadersFormatter = Json.format[ForwardedHeader]
  implicit val clientIdFormatter: Format[ClientId] = Json.valueFormat[ClientId]
  implicit val outboundNotificationFormatter = Json.format[OutboundNotification]
  implicit val updateCallBAckUrlRequestFormatter =Json.format[UpdateCallbackUrlRequest]
  implicit val applicationResponseformater = Json.format[ApplicationResponse]
  implicit val eventIdFormat: Format[EventId] = Json.valueFormat[EventId]
  implicit val actorFormat: Format[Actor] = Json.format[Actor]
  implicit val ppnsEventFormat: OFormat[PpnsCallBackUriUpdatedEvent] = Json.format[PpnsCallBackUriUpdatedEvent]

}

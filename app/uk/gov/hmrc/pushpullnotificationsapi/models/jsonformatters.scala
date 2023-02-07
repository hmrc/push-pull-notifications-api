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

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField.{HOUR_OF_DAY, MINUTE_OF_HOUR, NANO_OF_SECOND, SECOND_OF_MINUTE}
import java.time.{Instant, ZoneId}

import play.api.libs.json._
import uk.gov.hmrc.play.json.Union

import uk.gov.hmrc.pushpullnotificationsapi.connectors.ApiPlatformEventsConnector.{Actor, EventId, PpnsCallBackUriUpdatedEvent}
import uk.gov.hmrc.pushpullnotificationsapi.connectors.ApplicationResponse
import uk.gov.hmrc.pushpullnotificationsapi.models.InstantFormatter.{instantReads, instantWrites}
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications._

object InstantFormatter {

  val lenientFormatter: DateTimeFormatter = new DateTimeFormatterBuilder()
    .parseLenient()
    .parseCaseInsensitive()
    .appendPattern("uuuu-MM-dd['T'HH:mm:ss[.nnnnnn][Z]['Z']]")
    .parseDefaulting(NANO_OF_SECOND, 0)
    .parseDefaulting(SECOND_OF_MINUTE, 0)
    .parseDefaulting(MINUTE_OF_HOUR, 0)
    .parseDefaulting(HOUR_OF_DAY, 0)
    .toFormatter
    .withZone(ZoneId.of("UTC"))

  val instantReads: Reads[Instant] = Reads.instantReads(lenientFormatter)

  val instantWrites: Writes[Instant] = Writes.temporalWrites(new DateTimeFormatterBuilder()
    .appendPattern("uuuu-MM-dd'T'HH:mm:ss.nnnZ")
    .toFormatter
    .withZone(ZoneId.of("UTC")))
}

object ResponseFormatters {
  implicit val instantFormat: Format[Instant] = Format(instantReads, instantWrites)
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
  implicit val createWrappedNotificationResponseFormatter: OFormat[CreateWrappedNotificationResponse] = Json.format[CreateWrappedNotificationResponse]
  implicit val updateCallbackUrlResponseFormatter: OFormat[UpdateCallbackUrlResponse] = Json.format[UpdateCallbackUrlResponse]
  implicit val validateBoxOwnershipResponseFormatter: OFormat[ValidateBoxOwnershipResponse] = Json.format[ValidateBoxOwnershipResponse]
  implicit val clientSecretResponseFormatter: OFormat[ClientSecret] = Json.format[ClientSecret]
}

object RequestFormatters {
  implicit val instantFormat: Format[Instant] = Format(instantReads, instantWrites)
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
  implicit val applicationIdFormatter: Format[ApplicationId] = Json.valueFormat[ApplicationId]
  implicit val forwardedHeadersFormatter: OFormat[ForwardedHeader] = Json.format[ForwardedHeader]
  implicit val clientIdFormatter: Format[ClientId] = Json.valueFormat[ClientId]
  implicit val confirmationIdFormatter: Format[ConfirmationId] = Json.valueFormat[ConfirmationId]
  implicit val notificationIdFormatter: Format[NotificationId] = Json.valueFormat[NotificationId]
  implicit val updateCallBAckUrlRequestFormatter: OFormat[UpdateCallbackUrlRequest] = Json.format[UpdateCallbackUrlRequest]
  implicit val applicationResponseFormatter: OFormat[ApplicationResponse] = Json.format[ApplicationResponse]
  implicit val eventIdFormat: Format[EventId] = Json.valueFormat[EventId]
  implicit val actorFormat: Format[Actor] = Json.format[Actor]
  implicit val ppnsEventFormat: OFormat[PpnsCallBackUriUpdatedEvent] = Json.format[PpnsCallBackUriUpdatedEvent]
  implicit val outboundNotificationFormatter: OFormat[OutboundNotification] = Json.format[OutboundNotification]
  implicit val outboundConfirmationFormatter: OFormat[OutboundConfirmation] = Json.format[OutboundConfirmation]
}

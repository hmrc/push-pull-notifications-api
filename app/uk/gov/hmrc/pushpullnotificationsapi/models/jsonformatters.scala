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

package uk.gov.hmrc.pushpullnotificationsapi.models

import org.joda.time.DateTime
import play.api.libs.json._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.json.Union
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationId}


object ReactiveMongoFormatters {
  implicit val clientIdFormatter: Format[ClientId] = Json.valueFormat[ClientId]
  implicit val boxIdFormatter: Format[BoxId] = Json.valueFormat[BoxId]
  implicit val subscriberIdFormatter: Format[SubscriberId] = Json.valueFormat[SubscriberId]

  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val pushSubscriberFormats: OFormat[PushSubscriber] = Json.format[PushSubscriber]
  implicit val formatBoxCreator: Format[BoxCreator] = Json.format[BoxCreator]
  implicit val formatSubscriber: Format[Subscriber] = Union.from[Subscriber]("subscriptionType")
    .and[PushSubscriber](SubscriptionType.API_PUSH_SUBSCRIBER.toString)
    .format
  implicit val boxFormats: OFormat[Box] = Json.format[Box]
  implicit val notificationIdFormatter: Format[NotificationId] = Json.valueFormat[NotificationId]
  implicit val notificationsFormats: OFormat[Notification] =Json.format[Notification]
}

object ResponseFormatters{
  val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
  implicit val JodaDateReads: Reads[org.joda.time.DateTime] = JodaReads.jodaDateReads(dateFormat)
  implicit val JodaDateWrites: Writes[org.joda.time.DateTime] = JodaWrites.jodaDateWrites(dateFormat)
  implicit val JodaDateTimeFormat: Format[DateTime] = Format(JodaDateReads, JodaDateWrites)
  implicit val notificationIdFormatter: Format[NotificationId] = Json.valueFormat[NotificationId]
  implicit val boxIdFormatter: Format[BoxId] = Json.valueFormat[BoxId]
  implicit val clientIdFormatter: Format[ClientId] = Json.valueFormat[ClientId]
  implicit val formatBoxCreator: Format[BoxCreator] = Json.format[BoxCreator]
  implicit val subscriberIdFormatter: Format[SubscriberId] = Json.valueFormat[SubscriberId]
  implicit val pushSubscriberFormats: OFormat[PushSubscriber] = Json.format[PushSubscriber]
  implicit val formatSubscriber: Format[Subscriber] = Union.from[Subscriber]("subscriptionType")
    .and[PushSubscriber](SubscriptionType.API_PUSH_SUBSCRIBER.toString)
    .format
  implicit val boxFormats: OFormat[Box] = Json.format[Box]
  implicit val notificationFormatter: OFormat[Notification] = Json.format[Notification]
  implicit val createBoxResponseFormatter: OFormat[CreateBoxResponse] = Json.format[CreateBoxResponse]
  implicit val createNotificationResponseFormatter: OFormat[CreateNotificationResponse] = Json.format[CreateNotificationResponse]
}

object RequestFormatters {
  implicit val clientIdFormatter: Format[ClientId] = Json.valueFormat[ClientId]
  implicit val boxIdFormatter: Format[BoxId] = Json.valueFormat[BoxId]
  implicit val subscriberIdFormatter: Format[SubscriberId] = Json.valueFormat[SubscriberId]
  implicit val createBoxRequestFormatter: OFormat[CreateBoxRequest] = Json.format[CreateBoxRequest]
  implicit val subscribersRequestFormatter: OFormat[SubscribersRequest] = Json.format[SubscribersRequest]
  implicit val updateSubscribersRequestFormatter: OFormat[UpdateSubscribersRequest] = Json.format[UpdateSubscribersRequest]
}

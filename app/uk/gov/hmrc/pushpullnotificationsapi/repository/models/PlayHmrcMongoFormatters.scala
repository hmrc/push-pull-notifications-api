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

package uk.gov.hmrc.pushpullnotificationsapi.repository.models

import java.time.Instant

import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.play.json.Union

import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ConfirmationStatus, NotificationId, RetryableNotification}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.BoxFormat.boxFormats

private[repository] object PlayHmrcMongoFormatters extends URLFormatter {
  implicit val confirmationIdFormatter: Format[ConfirmationId] = Json.valueFormat[ConfirmationId]
  implicit val boxIdFormatter: Format[BoxId] = Json.valueFormat[BoxId]
  implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val pullSubscriberFormats: OFormat[PullSubscriber] = Json.format[PullSubscriber]
  implicit val pushSubscriberFormats: OFormat[PushSubscriber] = Json.format[PushSubscriber]
  implicit val formatBoxCreator: OFormat[BoxCreator] = Json.format[BoxCreator]

  implicit val formatSubscriber: OFormat[Subscriber] = Union.from[Subscriber]("subscriptionType")
    .and[PullSubscriber](SubscriptionType.API_PULL_SUBSCRIBER.toString)
    .and[PushSubscriber](SubscriptionType.API_PUSH_SUBSCRIBER.toString)
    .format
  implicit val notificationIdFormatter: Format[NotificationId] = Json.valueFormat[NotificationId]

  implicit val dbClientSecretFormatter: OFormat[DbClientSecret] = Json.format[DbClientSecret]
  implicit val dbClientFormatter: OFormat[DbClient] = Json.format[DbClient]
  implicit val dbNotificationFormatter: OFormat[DbNotification] = Json.format[DbNotification]
  implicit val retryableNotificationFormatter: OFormat[RetryableNotification] = Json.format[RetryableNotification]
  implicit val dbRetryableNotificationFormatter: OFormat[DbRetryableNotification] = Json.format[DbRetryableNotification]

  import play.api.libs.functional.syntax._

  implicit val confirmationRequestDBReads: Reads[ConfirmationRequestDB] = (
    (__ \ "confirmationId").read[ConfirmationId] and
      (__ \ "confirmationUrl").read[String] and
      (__ \ "notificationId").read[NotificationId] and
      // Read privateHeaders if it's there otherwise empty list
      (__ \ "privateHeaders").readNullable[List[PrivateHeader]].map(_.getOrElse(List.empty)) and
      (__ \ "status").read[ConfirmationStatus] and
      (__ \ "createdDateTime").read[Instant] and
      (__ \ "pushedDateTime").readNullable[Instant] and
      (__ \ "retryAfterDateTime").readNullable[Instant]
  )(ConfirmationRequestDB.apply _)

  implicit val confirmationRequestWrites: Writes[ConfirmationRequestDB] = Json.writes[ConfirmationRequestDB]
  implicit val confirmationRequestFormatter = Format(confirmationRequestDBReads, confirmationRequestWrites)
}

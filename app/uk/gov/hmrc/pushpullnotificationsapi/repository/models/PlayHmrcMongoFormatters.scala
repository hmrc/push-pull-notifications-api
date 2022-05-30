/*
 * Copyright 2022 HM Revenue & Customs
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

import org.joda.time.DateTime
import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Format, __, Json, OFormat, Reads}
import uk.gov.hmrc.play.json.Union
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationId, NotificationStatus, RetryableNotification}
import uk.gov.hmrc.pushpullnotificationsapi.models._

private[repository] object PlayHmrcMongoFormatters {
  implicit val applicationIdFormatter = Json.valueFormat[ApplicationId]

  implicit val notificationIdFormatter: Format[NotificationId] = Json.format[NotificationId]
  implicit val notificationPendingStatusFormatter: OFormat[NotificationStatus.PENDING.type] = Json.format[NotificationStatus.PENDING.type]
  implicit val notificationFailedStatusFormatter: OFormat[NotificationStatus.FAILED.type] = Json.format[NotificationStatus.FAILED.type]
  implicit val notificationAckStatusFormatter: OFormat[NotificationStatus.ACKNOWLEDGED.type] = Json.format[NotificationStatus.ACKNOWLEDGED.type]
  implicit val subscriptionTypePushFormatter: OFormat[SubscriptionType.API_PUSH_SUBSCRIBER.type] = Json.format[SubscriptionType.API_PUSH_SUBSCRIBER.type]
  implicit val subscriptionTypePullFormatter: OFormat[SubscriptionType.API_PULL_SUBSCRIBER.type] = Json.format[SubscriptionType.API_PULL_SUBSCRIBER.type]
  implicit val clientIdFormatter = Json.valueFormat[ClientId]
  implicit val boxIdFormatter = Json.valueFormat[BoxId]
  implicit val dateFormat = MongoJodaFormats.dateTimeFormat
  implicit val pullSubscriberFormats = Json.format[PullSubscriber]
  implicit val pushSubscriberFormats = Json.format[PushSubscriber]
  implicit val formatBoxCreator = Json.format[BoxCreator]
  implicit val formatSubscriber = Union.from[Subscriber]("subscriptionType")
    .and[PullSubscriber](SubscriptionType.API_PULL_SUBSCRIBER.toString)
    .and[PushSubscriber](SubscriptionType.API_PUSH_SUBSCRIBER.toString)
    .format

  implicit val dbClientSecretFormatter: OFormat[DbClientSecret] = Json.format[DbClientSecret]
  implicit val dbClientFormatter: OFormat[DbClient] = Json.format[DbClient]
  implicit val dbNotificationFormatter: OFormat[DbNotification] = Json.format[DbNotification]

  val boxWrites = Json.writes[Box]
  val boxReads: Reads[Box] = (
    (__ \ "boxId").read[BoxId] and
      (__ \ "boxName").read[String] and
      (__ \ "boxCreator").read[BoxCreator] and
      (__ \ "applicationId").readNullable[ApplicationId] and
      (__ \ "subscriber").readNullable[Subscriber] and
      (__ \ "clientManaged").readWithDefault(false)
    ) (Box.apply _)

  implicit val boxFormats = OFormat(boxReads, boxWrites)

  implicit val retryableNotificationFormatter: OFormat[RetryableNotification] = Json.format[RetryableNotification]
  implicit val dbRetryableNotificationFormatter: OFormat[DbRetryableNotification] = Json.format[DbRetryableNotification]


}

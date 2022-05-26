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
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Format, JsObject, JsResult, JsValue, Json, OFormat, __}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.json.Union
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationId, NotificationStatus, RetryableNotification}
import uk.gov.hmrc.pushpullnotificationsapi.models._

private[repository] object PlayHmrcMongoFormatters {
  implicit val clientIdFormatter: Format[ClientId] = Json.valueFormat[ClientId]
  implicit val boxIdFormatter: Format[BoxId] = Json.valueFormat[BoxId]
  implicit val applicationIdFormatter: Format[ApplicationId] = Json.valueFormat[ApplicationId]
  implicit val dateFormat: Format[DateTime] = MongoJodaFormats.dateTimeFormat
  implicit val pullSubscriberFormats: OFormat[PullSubscriber] = Json.format[PullSubscriber]
  implicit val pushSubscriberFormats: OFormat[PushSubscriber] = Json.format[PushSubscriber]
  implicit val formatBoxCreator: Format[BoxCreator] = Json.format[BoxCreator]
  implicit val formatSubscriber: Format[Subscriber] = Union.from[Subscriber]("subscriptionType")
    .and[PullSubscriber](SubscriptionType.API_PULL_SUBSCRIBER.toString)
    .and[PushSubscriber](SubscriptionType.API_PUSH_SUBSCRIBER.toString)
    .format

  val boxWrites = Json.writes[Box]
  val boxReads = (
    (__ \ "boxId").read[BoxId] and
      (__ \ "boxName").read[String] and
      (__ \ "boxCreator").read[BoxCreator] and
      (__ \ "applicationId").readNullable[ApplicationId] and
      (__ \ "subscriber").readNullable[Subscriber] and
      (__ \ "clientManaged").readWithDefault(false)
    ) { Box }

  implicit val boxFormats = OFormat(boxReads, boxWrites)

  implicit val formatBoxCreator: Format[BoxCreator] = Json.format[BoxCreator]

  implicit val notificationIdFormatter: Format[NotificationId] = Json.valueFormat[NotificationId]
  implicit val notificationPendingStatusFormatter: OFormat[NotificationStatus.PENDING.type] = Json.format[NotificationStatus.PENDING.type]
  implicit val notificationFailedStatusFormatter: OFormat[NotificationStatus.FAILED.type] = Json.format[NotificationStatus.FAILED.type]
  implicit val notificationAckStatusFormatter: OFormat[NotificationStatus.ACKNOWLEDGED.type] = Json.format[NotificationStatus.ACKNOWLEDGED.type]
  implicit val subscriptionTypePushFormatter: OFormat[SubscriptionType.API_PUSH_SUBSCRIBER.type] = Json.format[SubscriptionType.API_PUSH_SUBSCRIBER.type]
  implicit val subscriptionTypePullFormatter: OFormat[SubscriptionType.API_PULL_SUBSCRIBER.type] = Json.format[SubscriptionType.API_PULL_SUBSCRIBER.type]

  implicit val dbClientSecretFormatter: OFormat[DbClientSecret] = Json.format[DbClientSecret]
  implicit val dbClientFormatter: OFormat[DbClient] = Json.format[DbClient]
  implicit val dbNotificationFormatter: OFormat[DbNotification] = Json.format[DbNotification]
  implicit val retryableNotificationFormatter: OFormat[RetryableNotification] = Json.format[RetryableNotification]
  implicit val dbRetryableNotificationFormatter: OFormat[DbRetryableNotification] = Json.format[DbRetryableNotification]
}

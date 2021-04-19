/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.pushpullnotificationsapi.models.notifications

import java.util.UUID

import enumeratum.values.{StringEnum, StringEnumEntry, StringPlayJsonValueEnum}
import enumeratum.{Enum, EnumEntry, PlayJsonEnum}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.PENDING
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, BoxId}

import scala.collection.immutable


sealed abstract class MessageContentType(val value: String) extends StringEnumEntry

object MessageContentType extends StringEnum[MessageContentType] with StringPlayJsonValueEnum[MessageContentType] {
  val values: immutable.IndexedSeq[MessageContentType] = findValues

  case object APPLICATION_JSON extends MessageContentType("application/json")
  case object APPLICATION_XML extends MessageContentType("application/xml")
}

sealed trait NotificationStatus extends EnumEntry

object NotificationStatus extends Enum[NotificationStatus] with PlayJsonEnum[NotificationStatus] {
  val values: immutable.IndexedSeq[NotificationStatus] = findValues

  case object PENDING extends NotificationStatus
  case object ACKNOWLEDGED extends NotificationStatus
  case object FAILED extends NotificationStatus
}


case class NotificationId(value: UUID) extends AnyVal {
  def raw: String = value.toString
}

case class Notification(notificationId: NotificationId,
                        boxId: BoxId,
                        messageContentType: MessageContentType,
                        message: String,
                        status: NotificationStatus = PENDING,
                        createdDateTime: DateTime = DateTime.now(DateTimeZone.UTC),
                        readDateTime: Option[DateTime] = None,
                        pushedDateTime: Option[DateTime] = None,
                        retryAfterDateTime: Option[DateTime] = None)

object Notification {
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val formatBoxID: OFormat[BoxId] = Json.format[BoxId]
  implicit val formatNotificationID: OFormat[NotificationId] = Json.format[NotificationId]
  implicit val format: OFormat[Notification] = Json.format[Notification]
}

case class ForwardedHeader(key: String, value: String)
case class OutboundNotification(destinationUrl: String, forwardedHeaders: List[ForwardedHeader], payload: String)

case class RetryableNotification(notification: Notification, box: Box)

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

package uk.gov.hmrc.pushpullnotificationsapi.models.notifications

import java.time.Instant
import java.util.UUID
import scala.collection.immutable

import enumeratum.values.{StringEnum, StringEnumEntry, StringPlayJsonValueEnum}
import enumeratum.{Enum, EnumEntry, PlayJsonEnum}

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.PENDING
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, BoxId, ConfirmationId}
import uk.gov.hmrc.pushpullnotificationsapi.models.PrivateHeader

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
  override def toString() = value.toString()
}

object NotificationId {
  implicit val format = Json.valueFormat[NotificationId]
  def random: NotificationId = NotificationId(UUID.randomUUID())
}

case class Notification(
    notificationId: NotificationId,
    boxId: BoxId,
    messageContentType: MessageContentType,
    message: String,
    status: NotificationStatus = PENDING,
    createdDateTime: Instant = Instant.now,
    readDateTime: Option[Instant] = None,
    pushedDateTime: Option[Instant] = None,
    retryAfterDateTime: Option[Instant] = None)

object Notification {
  implicit val dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val format: OFormat[Notification] = Json.format[Notification]
}

sealed trait ConfirmationStatus extends EnumEntry

object ConfirmationStatus extends Enum[ConfirmationStatus] with PlayJsonEnum[ConfirmationStatus] {
  val values: immutable.IndexedSeq[ConfirmationStatus] = findValues

  case object PENDING extends ConfirmationStatus
  case object ACKNOWLEDGED extends ConfirmationStatus
  case object FAILED extends ConfirmationStatus
}

case class ForwardedHeader(key: String, value: String)

object ForwardedHeader {
  implicit val format = Json.format[ForwardedHeader]
}

case class OutboundNotification(destinationUrl: String, forwardedHeaders: List[ForwardedHeader], payload: String)

object OutboundNotification {
  implicit val format = Json.format[OutboundNotification]
}

case class OutboundConfirmation(confirmationId: ConfirmationId, notificationId: NotificationId, version: String, status: NotificationStatus, dateTime: Option[Instant], privateHeaders: List[PrivateHeader])

object OutboundConfirmation {
  implicit val format = Json.format[OutboundConfirmation]
}

case class RetryableNotification(notification: Notification, box: Box)

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

package uk.gov.hmrc.pushpullnotificationsapi.models.notifications

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.collection.immutable.ListSet

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.PENDING
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, BoxId, ConfirmationId, PrivateHeader}

sealed trait MessageContentType {
  def value: String = MessageContentType.value(this)
}

object MessageContentType {
  case object APPLICATION_JSON extends MessageContentType
  case object APPLICATION_XML extends MessageContentType

  val values: ListSet[MessageContentType] = ListSet[MessageContentType](APPLICATION_JSON, APPLICATION_XML)

  def apply(text: String): Option[MessageContentType] = MessageContentType.values.find(_.value == text)

  def value(m: MessageContentType): String = m match {
    case APPLICATION_JSON => "application/json"
    case APPLICATION_XML  => "application/xml"
  }

  import play.api.libs.json.Format
  import uk.gov.hmrc.apiplatform.modules.common.domain.services.SealedTraitJsonFormatting

  implicit val format: Format[MessageContentType] =
    SealedTraitJsonFormatting.createFormatFor[MessageContentType]("Message content type", MessageContentType.apply, MessageContentType.value)
}

sealed trait NotificationStatus

object NotificationStatus {
  case object PENDING extends NotificationStatus
  case object ACKNOWLEDGED extends NotificationStatus
  case object FAILED extends NotificationStatus

  val values: ListSet[NotificationStatus] = ListSet[NotificationStatus](PENDING, ACKNOWLEDGED, FAILED)

  def apply(text: String): Option[NotificationStatus] = NotificationStatus.values.find(_.toString() == text.toUpperCase)
  def unsafeApply(text: String): NotificationStatus = apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid NotificationStatus"))

  import play.api.libs.json.Format
  import uk.gov.hmrc.apiplatform.modules.common.domain.services.SealedTraitJsonFormatting
  implicit val format: Format[NotificationStatus] = SealedTraitJsonFormatting.createFormatFor[NotificationStatus]("Notification status", NotificationStatus.apply)
}

case class NotificationId(value: UUID) extends AnyVal {
  override def toString: String = value.toString
}

object NotificationId {
  implicit val format: Format[NotificationId] = Json.valueFormat[NotificationId]
  def random: NotificationId = NotificationId(UUID.randomUUID())
}

case class Notification(
    notificationId: NotificationId,
    boxId: BoxId,
    messageContentType: MessageContentType,
    message: String,
    status: NotificationStatus = PENDING,
    createdDateTime: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    readDateTime: Option[Instant] = None,
    pushedDateTime: Option[Instant] = None,
    retryAfterDateTime: Option[Instant] = None)

object Notification {
  implicit val dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val format: OFormat[Notification] = Json.format[Notification]
}

sealed trait ConfirmationStatus

object ConfirmationStatus {
  case object PENDING extends ConfirmationStatus
  case object ACKNOWLEDGED extends ConfirmationStatus
  case object FAILED extends ConfirmationStatus

  val values: ListSet[ConfirmationStatus] = ListSet[ConfirmationStatus](PENDING, ACKNOWLEDGED, FAILED)

  def apply(text: String): Option[ConfirmationStatus] = ConfirmationStatus.values.find(_.toString() == text.toUpperCase)

  import play.api.libs.json.Format
  import uk.gov.hmrc.apiplatform.modules.common.domain.services.SealedTraitJsonFormatting
  implicit val format: Format[ConfirmationStatus] = SealedTraitJsonFormatting.createFormatFor[ConfirmationStatus]("Confirmation status", ConfirmationStatus.apply)
}

case class ForwardedHeader(key: String, value: String)

object ForwardedHeader {
  implicit val format: OFormat[ForwardedHeader] = Json.format[ForwardedHeader]
}

case class OutboundNotification(destinationUrl: String, forwardedHeaders: List[ForwardedHeader], payload: String)

object OutboundNotification {
  implicit val format: OFormat[OutboundNotification] = Json.format[OutboundNotification]
}

case class OutboundConfirmation(
    confirmationId: ConfirmationId,
    notificationId: NotificationId,
    version: String,
    status: NotificationStatus,
    dateTime: Option[Instant],
    privateHeaders: List[PrivateHeader])

object OutboundConfirmation {
  implicit val format: OFormat[OutboundConfirmation] = Json.format[OutboundConfirmation]
}

case class RetryableNotification(notification: Notification, box: Box)

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

package uk.gov.hmrc.pushpullnotificationsapi.models

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.collection.immutable.ListSet

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.play.json.Union

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.{API_PULL_SUBSCRIBER, API_PUSH_SUBSCRIBER}

case class BoxId(value: UUID) extends AnyVal {
  override def toString: String = value.toString
}

object BoxId {
  implicit val format: Format[BoxId] = Json.valueFormat[BoxId]
  def random: BoxId = BoxId(UUID.randomUUID())
}

case class ConfirmationId(value: UUID) extends AnyVal {
  override def toString: String = value.toString
}

object ConfirmationId {
  implicit val format: Format[ConfirmationId] = Json.valueFormat[ConfirmationId]
  def random: ConfirmationId = ConfirmationId(UUID.randomUUID())
}

case class BoxCreator(clientId: ClientId)

object BoxCreator {
  implicit val format: OFormat[BoxCreator] = Json.format[BoxCreator]
}

sealed trait SubscriptionType

object SubscriptionType {
  case object API_PUSH_SUBSCRIBER extends SubscriptionType
  case object API_PULL_SUBSCRIBER extends SubscriptionType // Does this need to exist?

  val values: ListSet[SubscriptionType] = ListSet[SubscriptionType](API_PUSH_SUBSCRIBER, API_PULL_SUBSCRIBER)

  def apply(text: String): Option[SubscriptionType] = SubscriptionType.values.find(_.toString() == text.toUpperCase)

  import play.api.libs.json.Format
  import uk.gov.hmrc.apiplatform.modules.common.domain.services.SealedTraitJsonFormatting
  implicit val format: Format[SubscriptionType] = SealedTraitJsonFormatting.createFormatFor[SubscriptionType]("Subscription Type", SubscriptionType.apply)
}

sealed trait Subscriber {
  val subscribedDateTime: Instant
  val subscriptionType: SubscriptionType
}

object Subscriber {
  implicit private val pushSubscriberFormat: OFormat[PushSubscriber] = Json.format[PushSubscriber]
  implicit private val pullSubscriberFormat: OFormat[PullSubscriber] = Json.format[PullSubscriber]

  implicit val formatSubscriber: OFormat[Subscriber] = Union
    .from[Subscriber]("subscriptionType")
    .and[PullSubscriber](SubscriptionType.API_PULL_SUBSCRIBER.toString)
    .and[PushSubscriber](SubscriptionType.API_PUSH_SUBSCRIBER.toString)
    .format
}

class SubscriberContainer[+A](val elem: A)

case class PushSubscriber(callBackUrl: String, override val subscribedDateTime: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)) extends Subscriber {
  override val subscriptionType: SubscriptionType = API_PUSH_SUBSCRIBER
}

case class PullSubscriber(
    callBackUrl: String, // Remove callbackUrl
    override val subscribedDateTime: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS))
    extends Subscriber {
  override val subscriptionType: SubscriptionType = API_PULL_SUBSCRIBER
}

case class Box(
    boxId: BoxId,
    boxName: String,
    boxCreator: BoxCreator,
    applicationId: Option[ApplicationId] = None,
    subscriber: Option[Subscriber] = None,
    clientManaged: Boolean = false)

case class Client(id: ClientId, secrets: Seq[ClientSecretValue])

case class ClientSecretValue(value: String)

object ClientSecretValue {
  implicit val format: OFormat[ClientSecretValue] = Json.format[ClientSecretValue]
}

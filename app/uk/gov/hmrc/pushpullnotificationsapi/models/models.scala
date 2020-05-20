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

import java.util.UUID

import enumeratum.{Enum, EnumEntry, PlayJsonEnum}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.API_PUSH_SUBSCRIBER

import scala.collection.immutable

case class TopicCreator(clientId: String)

object TopicCreator {
  implicit val formats: OFormat[TopicCreator] = Json.format[TopicCreator]
}

sealed trait SubscriptionType extends EnumEntry

object SubscriptionType extends Enum[SubscriptionType] with PlayJsonEnum[SubscriptionType]  {
   val values: immutable.IndexedSeq[SubscriptionType] = findValues

  case object API_PUSH_SUBSCRIBER extends SubscriptionType
}

sealed trait Subscriber {
  val subscriberId: String
  val subscribedDateTime: DateTime
  val subscriptionType: SubscriptionType
}

class SubscriberContainer[+A] (val elem: A)

case class PushSubscriber(callBackUrl: String,
                          override val subscribedDateTime: DateTime = DateTime.now(DateTimeZone.UTC),
                          override val subscriberId: String = UUID.randomUUID().toString) extends Subscriber {
  override val subscriptionType: SubscriptionType = API_PUSH_SUBSCRIBER
}

case class Topic(topicId: String, topicName: String, topicCreator: TopicCreator, subscribers: List[Subscriber] = List.empty)

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

import play.api.libs.json.{Format, Json, OFormat}
//{
//  "_id" : ObjectId("5ea6a85b7d5c7bb2905a96d1"),
//  "topicId" : "6d16fcc1-62d3-4d88-9daa-1d11a50b99aa",
//  "topicName" : "topicName",
//  "topicCreator" : {
//     "clientId" : "ClientID1"
//    },
//  "subscribers" : [
//    {
//      "clientId" : "ClientID1",
//      "callBackUrl" : "some/endpoint",
//      "subscriptionType" : "API_PUSH_SUBSCRIBER"
//    }
//  ]
//}


//DB models
case class TopicCreator(clientId: String)

object TopicCreator {
  implicit val formats: OFormat[TopicCreator] = Json.format[TopicCreator]
}

object SubscriptionType extends Enumeration {
  type SubscriptionType = Value
  val API_PUSH_SUBSCRIBER: SubscriptionType.Value = Value
  implicit val SubscriptionTypeFormat: Format[SubscriptionType.Value] = EnumJson.enumFormat(SubscriptionType)
}

sealed trait Subscriber {
  val subscriberId: String
  val subscriptionType: SubscriptionType.Value
  val clientId: String
}

case class PushSubscriber(override val clientId: String, callBackUrl: String) extends Subscriber {
  override val subscriberId: String = UUID.randomUUID().toString
  override val subscriptionType: SubscriptionType.Value = SubscriptionType.API_PUSH_SUBSCRIBER
  implicit val formats: OFormat[PushSubscriber] = Json.format[PushSubscriber]
}

case class Topic(topicId: String, topicName: String, topicCreator: TopicCreator, subscribers: List[Subscriber] = List.empty)

object Topic{
  implicit val subscriptionFormats = ReactiveMongoFormatters.formatSubscriber
  implicit val formats: OFormat[Topic] = Json.format[Topic]
}




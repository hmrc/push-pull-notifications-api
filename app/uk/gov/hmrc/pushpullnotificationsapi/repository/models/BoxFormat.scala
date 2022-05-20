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

import play.api.libs.json._
import play.api.libs.functional.syntax._

import org.joda.time.DateTime
import play.api.libs.json.{Format, JsObject, JsResult, JsValue, Json, OFormat}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models.{
  ApplicationId,
  Box,
  BoxCreator,
  BoxId,
  ClientId,
  PullSubscriber,
  PushSubscriber,
  Subscriber
}
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType
import uk.gov.hmrc.play.json.Union

object BoxFormat extends OFormat[Box] {
  implicit private val applicationIdFormat = Json.format[ApplicationId]
  implicit private val clientIdFormatter: Format[ClientId] =
    Json.valueFormat[ClientId]
  implicit val boxIdFormatter: Format[BoxId] = Json.valueFormat[BoxId]
  implicit val boxCreatorFormat = Json.format[BoxCreator]
  implicit val pushSubscriberFormat = Json.format[PushSubscriber]
  implicit val pullSubscriberFormat = Json.format[PullSubscriber]
  implicit val subscriberFormat = Json.format[Subscriber]
  implicit val dateFormat: Format[DateTime] =
    ReactiveMongoFormats.dateTimeFormats
  implicit val formatSubscriber: Format[Subscriber] = Union
    .from[Subscriber]("subscriptionType")
    .and[PullSubscriber](SubscriptionType.API_PULL_SUBSCRIBER.toString)
    .and[PushSubscriber](SubscriptionType.API_PUSH_SUBSCRIBER.toString)
    .format

  private val boxReads = (
    (__ \ "boxId").read[BoxId] and
      (__ \ "boxName").read[String] and
      (__ \ "boxCreator").read[BoxCreator] and
      (__ \ "applicationId").readNullable[ApplicationId] and
      (__ \ "subscriber").readNullable[Subscriber] and
      (__ \ "clientManaged").readWithDefault(false)
  ) { Box }

  override def writes(o: Box): JsObject = { Json.writes[Box].writes(o) }
  override def reads(json: JsValue): JsResult[Box] = {
    boxReads.reads(json)
  }
}

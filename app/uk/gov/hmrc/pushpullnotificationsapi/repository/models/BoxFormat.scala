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

import play.api.libs.functional.syntax._
import play.api.libs.json.JsObject
import play.api.libs.json.JsResult
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.json.Union
import uk.gov.hmrc.pushpullnotificationsapi.models.ApplicationId
import uk.gov.hmrc.pushpullnotificationsapi.models.Box
import uk.gov.hmrc.pushpullnotificationsapi.models.BoxCreator
import uk.gov.hmrc.pushpullnotificationsapi.models.BoxId
import uk.gov.hmrc.pushpullnotificationsapi.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models.PullSubscriber
import uk.gov.hmrc.pushpullnotificationsapi.models.PushSubscriber
import uk.gov.hmrc.pushpullnotificationsapi.models.Subscriber
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType

/** */
object BoxFormat extends OFormat[Box] {
  implicit private val applicationIdFormat = Json.valueFormat[ApplicationId]
  implicit private val clientIdFormatter = Json.valueFormat[ClientId]
  implicit private val boxIdFormatter = Json.valueFormat[BoxId]
  implicit private val boxCreatorFormat = Json.format[BoxCreator]
  implicit private val dateFormat = ReactiveMongoFormats.dateTimeFormats
  implicit private val pushSubscriberFormat = Json.format[PushSubscriber]
  implicit private val pullSubscriberFormat = Json.format[PullSubscriber]
  implicit private val formatSubscriber = Union
    .from[Subscriber]("subscriptionType")
    .and[PullSubscriber](SubscriptionType.API_PULL_SUBSCRIBER.toString)
    .and[PushSubscriber](SubscriptionType.API_PUSH_SUBSCRIBER.toString)
    .format

  private val boxWrites = Json.writes[Box]
  private val boxReads = (
    (__ \ "boxId").read[BoxId] and
      (__ \ "boxName").read[String] and
      (__ \ "boxCreator").read[BoxCreator] and
      (__ \ "applicationId").readNullable[ApplicationId] and
      (__ \ "subscriber").readNullable[Subscriber] and
      (__ \ "clientManaged").readWithDefault(false)
    ) { Box }

  implicit val boxFormats = OFormat(boxReads, boxWrites)

  override def writes(box: Box): JsObject = {
    boxWrites.writes(box)
  }

  override def reads(json: JsValue): JsResult[Box] = {
    boxReads.reads(json)
  }
}
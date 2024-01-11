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

package uk.gov.hmrc.pushpullnotificationsapi.repository.models

import java.time.Instant

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.play.json.Union

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.pushpullnotificationsapi.models._

/** */
object BoxFormat extends OFormat[Box] {

  implicit private val boxIdFormatter: Format[BoxId] = Json.valueFormat[BoxId]
  implicit private val boxCreatorFormat: OFormat[BoxCreator] = Json.format[BoxCreator]
  implicit private val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit private val pushSubscriberFormat: OFormat[PushSubscriber] = Json.format[PushSubscriber]
  implicit private val pullSubscriberFormat: OFormat[PullSubscriber] = Json.format[PullSubscriber]

  implicit private val formatSubscriber: OFormat[Subscriber] = Union
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

  implicit val boxFormats: OFormat[Box] = OFormat(boxReads, boxWrites)

  override def writes(box: Box): JsObject = {
    boxWrites.writes(box)
  }

  override def reads(json: JsValue): JsResult[Box] = {
    boxReads.reads(json)
  }
}

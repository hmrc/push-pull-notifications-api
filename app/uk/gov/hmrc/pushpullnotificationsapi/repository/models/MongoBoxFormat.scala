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

import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.play.json.Union

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.pushpullnotificationsapi.models.*

/** */
object MongoBoxFormat {
  private given Format[Instant] = MongoJavatimeFormats.instantFormat
  private given OFormat[PullSubscriber] = Json.format[PullSubscriber]
  private given OFormat[PushSubscriber] = Json.format[PushSubscriber]

  given OFormat[Subscriber] = Union
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
      (__ \ "subscriber").readNullable[Subscriber]
  )((boxId, boxName, boxCreator, applicationId, subscriber) => Box(boxId, boxName, boxCreator, applicationId, subscriber))

  given OFormat[Box] = OFormat(boxReads, boxWrites)
}

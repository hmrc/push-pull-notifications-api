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

import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.play.json.Union

import uk.gov.hmrc.pushpullnotificationsapi.models.*
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ConfirmationStatus, NotificationId, RetryableNotification}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.MongoBoxFormat.given

private[repository] object PlayHmrcMongoFormatters extends URLFormatter {
  given Format[Instant] = MongoJavatimeFormats.instantFormat
  given OFormat[PullSubscriber] = Json.format[PullSubscriber]
  given OFormat[PushSubscriber] = Json.format[PushSubscriber]

  given OFormat[Subscriber] = Union.from[Subscriber]("subscriptionType")
    .and[PullSubscriber](SubscriptionType.API_PULL_SUBSCRIBER.toString)
    .and[PushSubscriber](SubscriptionType.API_PUSH_SUBSCRIBER.toString)
    .format

  given OFormat[DbNotification] = Json.format[DbNotification]
  given OFormat[RetryableNotification] = Json.format[RetryableNotification]
  given OFormat[DbRetryableNotification] = Json.format[DbRetryableNotification]

  import play.api.libs.functional.syntax.*

  private val confirmationRequestDBReads: Reads[ConfirmationRequestDB] = (
    (__ \ "confirmationId").read[ConfirmationId] and
      (__ \ "confirmationUrl").read[String] and
      (__ \ "notificationId").read[NotificationId] and
      // Read privateHeaders if it's there otherwise empty list
      (__ \ "privateHeaders").readNullable[List[PrivateHeader]].map(_.getOrElse(List.empty)) and
      (__ \ "status").read[ConfirmationStatus] and
      (__ \ "createdDateTime").read[Instant] and
      (__ \ "pushedDateTime").readNullable[Instant] and
      (__ \ "retryAfterDateTime").readNullable[Instant]
  )((c, s, n, p, s2, c2, p2, r) => ConfirmationRequestDB(c, s, n, p, s2, c2, p2, r))

  private val confirmationRequestWrites: Writes[ConfirmationRequestDB] = Json.writes[ConfirmationRequestDB]
  given Format[ConfirmationRequestDB] = Format(confirmationRequestDBReads, confirmationRequestWrites)
}

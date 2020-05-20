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

package uk.gov.hmrc.pushpullnotificationsapi.models.notifications

import java.util.UUID

import enumeratum.{Enum, EnumEntry, PlayJsonEnum}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.RECEIVED

import scala.collection.immutable


sealed trait NotificationContentType extends EnumEntry

object NotificationContentType extends Enum[NotificationContentType] with PlayJsonEnum[NotificationContentType] {
  val values: immutable.IndexedSeq[NotificationContentType] = findValues

  case object APPLICATION_JSON extends NotificationContentType

  case object APPLICATION_XML extends NotificationContentType

  case object UNSUPPORTED extends NotificationContentType

}

sealed trait NotificationStatus extends EnumEntry

object NotificationStatus extends Enum[NotificationStatus] with PlayJsonEnum[NotificationStatus] {
  val values: immutable.IndexedSeq[NotificationStatus] = findValues

  case object RECEIVED extends NotificationStatus

  case object READ extends NotificationStatus

  case object UNKNOWN extends NotificationStatus

}


case class Notification(notificationId: UUID,
                        topicId: String,
                        notificationContentType: NotificationContentType,
                        message: String,
                        status: NotificationStatus = RECEIVED,
                        createdDateTime: DateTime = DateTime.now(DateTimeZone.UTC),
                        readDateTime: Option[DateTime] = None,
                        pushedDateTime: Option[DateTime] = None)

object Notification {
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats

  implicit val format: OFormat[Notification] = Json.format[Notification]
}

//{
//    "_id" : ObjectId("5ebbb1a07d90a80ffb42af91"),
//    "notificationId" : "16002adf-fdf9-42a1-8ab9-c5ca3cdee1ef",
//    "topicId" : "someTopicId",
//    "notificationContentType" : "APPLICATION_JSON",
//    "message" : "{",
//    "status" : "RECEIVED",
//    "createdDateTime" : "2020-05-13T08:36:48.625+0000"
//}
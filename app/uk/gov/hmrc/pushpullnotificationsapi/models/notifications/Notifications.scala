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

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Format
import uk.gov.hmrc.pushpullnotificationsapi.models.EnumJson


object NotificationContentType extends Enumeration {
  type NotificationContentType = Value
  val APPLICATION_JSON: NotificationContentType.Value = Value
  val APPLICATION_XML: NotificationContentType.Value = Value
  val UNSUPPORTED: NotificationContentType.Value = Value
  implicit val AllowedContentTypeFormat: Format[NotificationContentType.Value] = EnumJson.enumFormat(NotificationContentType)
}

object NotificationStatus extends Enumeration {
  type NotificationStatus = Value
  val RECEIVED: NotificationStatus.Value = Value
  implicit val NotificationStatusFormat: Format[NotificationStatus.Value] = EnumJson.enumFormat(NotificationStatus)
}


case class Notification(notificationId : UUID,
                        topicId : String,
                        notificationContentType: NotificationContentType.Value,
                        message: String,
                        status: NotificationStatus.Value = NotificationStatus.RECEIVED,
                        createdDateTime: DateTime = DateTime.now(DateTimeZone.UTC),
                        readDateTime: Option[DateTime] = None,
                        pushedDateTime: Option[DateTime] = None)


//{
//    "_id" : ObjectId("5ebbb1a07d90a80ffb42af91"),
//    "notificationId" : "16002adf-fdf9-42a1-8ab9-c5ca3cdee1ef",
//    "topicId" : "someTopicId",
//    "notificationContentType" : "APPLICATION_JSON",
//    "message" : "{",
//    "status" : "RECEIVED",
//    "createdDateTime" : "2020-05-13T08:36:48.625+0000"
//}
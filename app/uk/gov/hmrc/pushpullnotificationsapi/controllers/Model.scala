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

package uk.gov.hmrc.pushpullnotificationsapi.controllers

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models.BoxId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.PENDING
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.Notification.{dateFormat, formatBoxID, formatNotificationID}

case class NotificationResponse(notificationId: NotificationId,
                        boxId: BoxId,
                        messageContentType: MessageContentType,
                        message: String,
                        status: NotificationStatus = PENDING,
                        createdDateTime: DateTime = DateTime.now(DateTimeZone.UTC),
                        readDateTime: Option[DateTime] = None,
                        pushedDateTime: Option[DateTime] = None)

object NotificationResponse {
  implicit val format: OFormat[NotificationResponse] = Json.format[NotificationResponse]

  def fromNotification(notification: Notification): NotificationResponse = {
    NotificationResponse(notification.notificationId, notification.boxId, notification.messageContentType, notification.message,
      notification.status, notification.createdDateTime, notification.readDateTime, notification.pushedDateTime)
  }
}



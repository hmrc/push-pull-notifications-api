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
import java.time.temporal.ChronoUnit

import uk.gov.hmrc.crypto.{Crypted, PlainText}

import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.PENDING
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications._
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, BoxId}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbNotification.{fromNotification, toNotification}
import uk.gov.hmrc.pushpullnotificationsapi.services.LocalCrypto

case class DbNotification(
    notificationId: NotificationId,
    boxId: BoxId,
    messageContentType: MessageContentType,
    encryptedMessage: String,
    status: NotificationStatus = PENDING,
    createdDateTime: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    readDateTime: Option[Instant] = None,
    pushedDateTime: Option[Instant] = None,
    retryAfterDateTime: Option[Instant] = None)

private[repository] object DbNotification {

  def fromNotification(notification: Notification, crypto: LocalCrypto): DbNotification = {
    DbNotification(
      notification.notificationId,
      notification.boxId,
      notification.messageContentType,
      crypto.encrypt(PlainText(notification.message)).value,
      notification.status,
      notification.createdDateTime,
      notification.readDateTime,
      notification.pushedDateTime,
      notification.retryAfterDateTime
    )
  }

  def toNotification(dbNotification: DbNotification, crypto: LocalCrypto): Notification = {
    Notification(
      dbNotification.notificationId,
      dbNotification.boxId,
      dbNotification.messageContentType,
      crypto.decrypt(Crypted(dbNotification.encryptedMessage)).value,
      dbNotification.status,
      dbNotification.createdDateTime,
      dbNotification.readDateTime,
      dbNotification.pushedDateTime,
      dbNotification.retryAfterDateTime
    )
  }
}

private[repository] case class DbRetryableNotification(notification: DbNotification, box: Box)

private[repository] object DbRetryableNotification {

  def fromRetryableNotification(retryableNotification: RetryableNotification, crypto: LocalCrypto): DbRetryableNotification = {
    DbRetryableNotification(fromNotification(retryableNotification.notification, crypto), retryableNotification.box)
  }

  def toRetryableNotification(dbRetryableNotification: DbRetryableNotification, crypto: LocalCrypto): RetryableNotification = {
    RetryableNotification(toNotification(dbRetryableNotification.notification, crypto), dbRetryableNotification.box)
  }

  def toRetryableNotification(box: Box, crypto: LocalCrypto)(dbNotification: DbNotification): RetryableNotification = {
    RetryableNotification(toNotification(dbNotification, crypto), box)
  }
}

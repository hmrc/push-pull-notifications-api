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

import java.net.URL
import java.time.Instant
import scala.util.Try

import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ConfirmationStatus, NotificationId}
import uk.gov.hmrc.pushpullnotificationsapi.models.{ConfirmationId, PrivateHeader}

case class ConfirmationRequest(
    confirmationId: ConfirmationId,
    confirmationUrl: URL,
    notificationId: NotificationId,
    privateHeaders: List[PrivateHeader],
    status: ConfirmationStatus = ConfirmationStatus.PENDING,
    createdDateTime: Instant = Instant.now,
    pushedDateTime: Option[Instant] = None,
    retryAfterDateTime: Option[Instant] = None) {

  def toDB: ConfirmationRequestDB =
    ConfirmationRequestDB(
      this.confirmationId,
      this.confirmationUrl.toString,
      this.notificationId,
      this.privateHeaders,
      this.status,
      this.createdDateTime,
      this.pushedDateTime,
      this.retryAfterDateTime
    )
}

// TODO - remove this and replace with above AFTER all bad URLs are expired from DB
case class ConfirmationRequestDB(
    confirmationId: ConfirmationId,
    confirmationUrl: String,
    notificationId: NotificationId,
    privateHeaders: List[PrivateHeader],
    status: ConfirmationStatus = ConfirmationStatus.PENDING,
    createdDateTime: Instant = Instant.now,
    pushedDateTime: Option[Instant] = None,
    retryAfterDateTime: Option[Instant] = None) {

  def toNonDb: Option[ConfirmationRequest] = {
    Try {
      new URL(this.confirmationUrl)
    }
      .toOption
      .filter(_.getProtocol().equals("https")) // Discard bad protocol in existing records
      .map(url =>
        ConfirmationRequest(this.confirmationId, url, this.notificationId, this.privateHeaders, this.status, this.createdDateTime, this.pushedDateTime, this.retryAfterDateTime)
      )
  }
}

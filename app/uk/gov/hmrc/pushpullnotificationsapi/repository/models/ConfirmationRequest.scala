/*
 * Copyright 2023 HM Revenue & Customs
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

import uk.gov.hmrc.pushpullnotificationsapi.models.ConfirmationId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.PENDING
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationId, NotificationStatus}
import java.net.URL

case class ConfirmationRequest(
    confirmationId: ConfirmationId,
    confirmationUrl: URL,
    notificationId: NotificationId,
    status: NotificationStatus = PENDING,
    createdDateTime: Instant = Instant.now,
    pushedDateTime: Option[Instant] = None,
    retryAfterDateTime: Option[Instant] = None)

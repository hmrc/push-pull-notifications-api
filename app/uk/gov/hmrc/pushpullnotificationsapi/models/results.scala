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

package uk.gov.hmrc.pushpullnotificationsapi.models

import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.Notification

sealed trait BoxCreateResult
final case class BoxCreateFailedResult(message: String) extends BoxCreateResult
sealed trait BoxCreateSuccessResult extends BoxCreateResult
final case class BoxCreatedResult(boxId: BoxId) extends BoxCreateSuccessResult
final case class BoxRetrievedResult(boxId: BoxId) extends BoxCreateSuccessResult

sealed trait NotificationCreateServiceResult

sealed trait NotificationCreateServiceFailedResult extends NotificationCreateServiceResult
sealed trait NotificationCreateServiceSuccessResult extends NotificationCreateServiceResult

final case class NotificationCreateSuccessResult() extends NotificationCreateServiceSuccessResult
final case class NotificationCreateFailedDuplicateResult(message: String) extends NotificationCreateServiceFailedResult
final case class NotificationCreateFailedBoxIdNotFoundResult(message: String) extends NotificationCreateServiceFailedResult

sealed trait GetNotificationCreateServiceResult

sealed trait GetNotificationsServiceFailedResult extends GetNotificationCreateServiceResult
sealed trait GetNotificationsServiceSuccessResult extends GetNotificationCreateServiceResult


final case class GetNotificationsServiceBoxNotFoundResult(message: String) extends GetNotificationsServiceFailedResult
final case class GetNotificationsServiceUnauthorisedResult(message: String) extends GetNotificationsServiceFailedResult

final case class GetNotificationsSuccessRetrievedResult(notifications: List[Notification]) extends GetNotificationsServiceSuccessResult




sealed trait PushConnectorResult
final case class PushConnectorSuccessResult() extends PushConnectorResult
final case class PushConnectorFailedResult(throwable: Throwable) extends PushConnectorResult

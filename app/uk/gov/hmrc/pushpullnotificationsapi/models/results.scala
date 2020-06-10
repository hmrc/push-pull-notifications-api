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

sealed trait TopicCreateResult
final case class TopicCreateFailedResult(message: String) extends TopicCreateResult
sealed trait TopicCreateSuccessResult extends TopicCreateResult
final case class TopicCreatedResult(topicId: TopicId) extends TopicCreateSuccessResult
final case class TopicRetrievedResult(topicId: TopicId) extends TopicCreateSuccessResult

sealed trait NotificationServiceResult
abstract class NotificationsServiceFailedResult() extends NotificationServiceResult
abstract class NotificationsServiceSuccessResult() extends NotificationServiceResult

case class NotificationsServiceTopicNotFoundResult(message: String) extends NotificationsServiceFailedResult
case class NotificationsServiceUnauthorisedResult(message: String) extends NotificationsServiceFailedResult

case class GetNotificationsSuccessRetrievedResult(notifications: List[Notification]) extends NotificationsServiceSuccessResult

case class SaveNotificationFailedDuplicateNotificationResult(message: String) extends NotificationsServiceFailedResult
case class SaveNotificationSuccessResult() extends NotificationsServiceSuccessResult

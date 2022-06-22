/*
 * Copyright 2022 HM Revenue & Customs
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

sealed trait CreateBoxResult
final case class BoxCreateFailedResult(message: String) extends CreateBoxResult
sealed trait BoxCreateSuccessResult extends CreateBoxResult
final case class BoxCreatedResult(box: Box) extends BoxCreateSuccessResult
final case class BoxRetrievedResult(box: Box) extends BoxCreateSuccessResult

sealed trait DeleteBoxResult
final case class BoxDeleteFailedResult(message: String) extends DeleteBoxResult
final case class BoxDeleteNotFoundResult() extends DeleteBoxResult
final case class BoxDeleteAccessDeniedResult() extends DeleteBoxResult
final case class BoxDeleteSuccessfulResult() extends DeleteBoxResult

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


sealed trait AcknowledgeNotificationsServiceResult

sealed trait AcknowledgeNotificationsServiceResultSuccess extends AcknowledgeNotificationsServiceResult
sealed trait AcknowledgeNotificationsServiceResultFailure extends AcknowledgeNotificationsServiceResult

final case class AcknowledgeNotificationsServiceBoxNotFoundResult(message: String) extends AcknowledgeNotificationsServiceResultFailure
final case class AcknowledgeNotificationsServiceUnauthorisedResult(message: String) extends AcknowledgeNotificationsServiceResultFailure
final case class AcknowledgeNotificationsSuccessUpdatedResult(result: Boolean) extends AcknowledgeNotificationsServiceResultSuccess


sealed trait PushConnectorResult
final case class PushConnectorSuccessResult() extends PushConnectorResult
final case class PushConnectorFailedResult(errorMessage: String) extends PushConnectorResult

sealed trait UpdateCallbackUrlResult
sealed trait UpdateCallbackUrlSuccessResult extends UpdateCallbackUrlResult
sealed trait UpdateCallbackUrlFailedResult extends UpdateCallbackUrlResult

final case class CallbackUrlUpdated() extends UpdateCallbackUrlSuccessResult
final case class CallbackUrlUpdatedWithBlank() extends UpdateCallbackUrlSuccessResult
final case class BoxIdNotFound() extends UpdateCallbackUrlFailedResult
final case class UnableToUpdateCallbackUrl(errorMessage: String) extends UpdateCallbackUrlFailedResult
final case class CallbackValidationFailed(errorMessage: String) extends UpdateCallbackUrlFailedResult
final case class UpdateCallbackUrlUnauthorisedResult() extends UpdateCallbackUrlFailedResult

sealed trait ValidateBoxOwnerResult
final case class ValidateBoxOwnerNotFoundResult(errorMessage: String) extends ValidateBoxOwnerResult
final case class ValidateBoxOwnerSuccessResult() extends ValidateBoxOwnerResult
final case class ValidateBoxOwnerFailedResult(errorMessage: String) extends ValidateBoxOwnerResult

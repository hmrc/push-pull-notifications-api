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

import org.joda.time.DateTime
import play.api.mvc.{Request, WrappedRequest}
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus

case class CreateBoxRequest(boxName: String, clientId: String)

case class ValidatedCreateBoxRequest[A](createBoxRequest: CreateBoxRequest, request: Request[A])
  extends WrappedRequest[A](request)

case class SubscriberRequest(callBackUrl: String, subscriberType: SubscriptionType, subscriberId: Option[String] = None)

case class UpdateSubscriberRequest(subscriber: SubscriberRequest)

// Notifications
case class AuthenticatedNotificationRequest[A](clientId: ClientId, request: Request[A])

case class NotificationQueryParams(status: Option[NotificationStatus],
                                   fromDate: Option[DateTime],
                                   toDate: Option[DateTime])

case class ValidatedNotificationQueryRequest[A](clientId: ClientId, params: NotificationQueryParams, request: Request[A])


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

package uk.gov.hmrc.pushpullnotificationsapi.models

import java.net.URL
import java.time.Instant
import scala.util.Try

import play.api.libs.json.{Json, OFormat}
import play.api.mvc.Request

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationId, NotificationStatus}

case class CreateBoxRequest(boxName: String, clientId: ClientId)

object CreateBoxRequest {
  implicit val format: OFormat[CreateBoxRequest] = Json.format[CreateBoxRequest]
}

case class SubscriberRequest(callBackUrl: String, subscriberType: SubscriptionType)

object SubscriberRequest {
  implicit val format: OFormat[SubscriberRequest] = Json.format[SubscriberRequest]
}

case class UpdateSubscriberRequest(subscriber: SubscriberRequest)

object UpdateSubscriberRequest {
  implicit val format: OFormat[UpdateSubscriberRequest] = Json.format[UpdateSubscriberRequest]
}

case class UpdateCallbackUrlRequest(clientId: ClientId, callbackUrl: String) {

  def isInvalid: Boolean = {
    this.clientId.value.isEmpty
  }
}

object UpdateCallbackUrlRequest {
  implicit val format: OFormat[UpdateCallbackUrlRequest] = Json.format[UpdateCallbackUrlRequest]
}

case class UpdateManagedCallbackUrlRequest(callbackUrl: String)

object UpdateManagedCallbackUrlRequest {
  implicit val format: OFormat[UpdateManagedCallbackUrlRequest] = Json.format[UpdateManagedCallbackUrlRequest]
}

case class ValidateBoxOwnershipRequest(boxId: BoxId, clientId: ClientId)

object ValidateBoxOwnershipRequest {
  implicit val format: OFormat[ValidateBoxOwnershipRequest] = Json.format[ValidateBoxOwnershipRequest]
}

case class PrivateHeader(name: String, value: String)

object PrivateHeader {
  implicit val format: OFormat[PrivateHeader] = Json.format[PrivateHeader]
}

case class WrappedNotificationBody(body: String, contentType: String)

object WrappedNotificationBody {
  import play.api.libs.json._

  implicit val format: OFormat[WrappedNotificationBody] = Json.format[WrappedNotificationBody]
}

case class WrappedNotificationRequest(notification: WrappedNotificationBody, version: String, confirmationUrl: Option[URL], privateHeaders: List[PrivateHeader])

trait URLFormatter {
  import play.api.libs.json._
  import scala.util.{Failure, Success}

  val fromString: String => JsResult[URL] = rawText => {
    Try[URL] {
      new URL(rawText)
    } match {
      case Success(v: URL) => JsSuccess(v)
      case Failure(_)      => JsError("some error")
    }
  }

  implicit val readsURL: Reads[URL] = implicitly[Reads[String]].flatMapResult(fromString)
  implicit val writesURL: Writes[URL] = implicitly[Writes[String]].contramap(url => url.toString)
}

object URLFormatter extends URLFormatter

object WrappedNotificationRequest {
  import play.api.libs.json._
  import play.api.libs.functional.syntax._
  import URLFormatter._

  implicit val reads: Reads[WrappedNotificationRequest] = (
    (__ \ "notification").read[WrappedNotificationBody] and
      (__ \ "version").read[String] and
      (__ \ "confirmationUrl").readNullable[URL] and
      // Read privateHeaders if it's there otherwise empty list
      (__ \ "privateHeaders").readNullable[List[PrivateHeader]].map(_.getOrElse(List.empty))
  )(WrappedNotificationRequest.apply(_, _, _, _))

  implicit val writes: OWrites[WrappedNotificationRequest] = Json.writes[WrappedNotificationRequest]
}
// Notifications

case class AcknowledgeNotificationsRequest(notificationIds: List[NotificationId])

object AcknowledgeNotificationsRequest {
  implicit val format: OFormat[AcknowledgeNotificationsRequest] = Json.format[AcknowledgeNotificationsRequest]
}

case class AuthenticatedNotificationRequest[A](clientId: ClientId, request: Request[A])

case class NotificationQueryParams(status: Option[NotificationStatus], fromDate: Option[Instant], toDate: Option[Instant])

case class ValidatedNotificationQueryRequest[A](clientId: ClientId, params: NotificationQueryParams, request: Request[A])

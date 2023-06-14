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

package uk.gov.hmrc.pushpullnotificationsapi.models

import java.net.URL
import java.time.Instant
import scala.util.Try

import play.api.libs.json.Json
import play.api.mvc.{Request, WrappedRequest}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationId, NotificationStatus}

case class CreateBoxRequest(boxName: String, clientId: ClientId)

object CreateBoxRequest {
  implicit val format = Json.format[CreateBoxRequest]
}

case class CreateClientManagedBoxRequest(boxName: String)

object CreateClientManagedBoxRequest {
  implicit val format = Json.format[CreateClientManagedBoxRequest]
}

case class SubscriberRequest(callBackUrl: String, subscriberType: SubscriptionType)

object SubscriberRequest {
  implicit val format = Json.format[SubscriberRequest]
}

case class UpdateSubscriberRequest(subscriber: SubscriberRequest)

object UpdateSubscriberRequest {
  implicit val format = Json.format[UpdateSubscriberRequest]
}

case class UpdateCallbackUrlRequest(clientId: ClientId, callbackUrl: String) {

  def isInvalid(): Boolean = {
    this.clientId.value.isEmpty
  }
}

object UpdateCallbackUrlRequest {
  implicit val format = Json.format[UpdateCallbackUrlRequest]
}

case class UpdateManagedCallbackUrlRequest(callbackUrl: String)

object UpdateManagedCallbackUrlRequest {
  implicit val format = Json.format[UpdateManagedCallbackUrlRequest]
}

case class ValidateBoxOwnershipRequest(boxId: BoxId, clientId: ClientId)

object ValidateBoxOwnershipRequest {
  implicit val format = Json.format[ValidateBoxOwnershipRequest]
}

case class PrivateHeader(name: String, value: String)

object PrivateHeader {
  implicit val format = Json.format[PrivateHeader]
}

case class WrappedNotificationBody(body: String, contentType: String)

object WrappedNotificationBody {
  import play.api.libs.json._

  implicit val format = Json.format[WrappedNotificationBody]
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
      (__ \ "privateHeaders").read[List[PrivateHeader]].orElse(Reads.pure(List.empty[PrivateHeader]))
  )(WrappedNotificationRequest.apply(_, _, _, _))

  implicit val writes = Json.writes[WrappedNotificationRequest]
}
// Notifications

case class AcknowledgeNotificationsRequest(notificationIds: List[NotificationId])

object AcknowledgeNotificationsRequest {
  implicit val format = Json.format[AcknowledgeNotificationsRequest]
}

case class ValidatedAcknowledgeNotificationsRequest(boxId: BoxId, notificationIds: Set[NotificationId])

object ValidatedAcknowledgeNotificationsRequest {
  implicit val format = Json.format[ValidatedAcknowledgeNotificationsRequest]
}

// internal use only no need for json formats
case class ValidatedCreateBoxRequest[A](createBoxRequest: CreateBoxRequest, request: Request[A]) extends WrappedRequest[A](request)

case class AuthenticatedNotificationRequest[A](clientId: ClientId, request: Request[A])

case class NotificationQueryParams(status: Option[NotificationStatus], fromDate: Option[Instant], toDate: Option[Instant])

case class ValidatedNotificationQueryRequest[A](clientId: ClientId, params: NotificationQueryParams, request: Request[A])

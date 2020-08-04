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

package uk.gov.hmrc.pushpullnotificationsapi.connectors

import com.google.inject.Inject
import javax.inject.Singleton
import play.api.Logger
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, UnprocessableEntityException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.connectors.PushConnector.{PushConnectorResponse, VerifyCallbackUrlRequest, VerifyCallbackUrlResponse, fromAddCallbackUrlRequest}
import uk.gov.hmrc.pushpullnotificationsapi.models.ConnectorFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.MessageContentType.APPLICATION_JSON
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.OutboundNotification
import uk.gov.hmrc.pushpullnotificationsapi.models.{UpdateCallbackUrlRequest, PushConnectorFailedResult, PushConnectorResult, PushConnectorSuccessResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class PushConnector @Inject()(http: HttpClient, appConfig: AppConfig)(implicit ec: ExecutionContext) {

  def send(pushNotificationRequest: OutboundNotification)(implicit hc: HeaderCarrier): Future[PushConnectorResult] = {
    doSend(pushNotificationRequest, hc)
  }

  private def doSend(notification: OutboundNotification, hc: HeaderCarrier): Future[PushConnectorResult] = {
    val url = s"${appConfig.outboundNotificationsUrl}/notify"

    val authorizationKey = appConfig.gatewayAuthToken
    Logger.debug(s"Calling outbound notification gateway url=${notification.destinationUrl} \nheaders=${hc.headers} \npayload= ${notification.payload}")

    implicit val modifiedHeaderCarrier: HeaderCarrier =
      hc.copy(authorization = Some(Authorization(authorizationKey)))
        .withExtraHeaders("Content-Type" -> APPLICATION_JSON.value)

    http.POST[OutboundNotification, PushConnectorResponse](url, notification)
      .map(_.successful) map {
        case true => PushConnectorSuccessResult()
        case false => PushConnectorFailedResult(new UnprocessableEntityException("PPNS Gateway was unable to successfully deliver notification"))
      } recover {
        case NonFatal(e) => PushConnectorFailedResult(e)
      }
  }

  def verifyCallbackUrl(addCallbackUrlRequest: UpdateCallbackUrlRequest): Future[Boolean] = { // Response type here
    val authorizationKey = appConfig.gatewayAuthToken
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization(authorizationKey)))

    val url = s"${appConfig.outboundNotificationsUrl}/verify-callback-url"
    http.POST[VerifyCallbackUrlRequest, VerifyCallbackUrlResponse](url, fromAddCallbackUrlRequest(addCallbackUrlRequest)).map(_.successful)
    // Use doSend() or some version of it with response types
  }
}

object PushConnector {
  implicit val pushConnectorResponseFormat: OFormat[PushConnectorResponse] = Json.format[PushConnectorResponse]
  private[connectors] case class PushConnectorResponse(successful: Boolean)

  implicit val verifyCallbackUrlRequestFormat: OFormat[VerifyCallbackUrlRequest] = Json.format[VerifyCallbackUrlRequest]
  implicit val verifyCallbackUrlResponseFormat: OFormat[VerifyCallbackUrlResponse] = Json.format[VerifyCallbackUrlResponse]
  private[connectors] case class VerifyCallbackUrlRequest(callbackUrl: String, verifyToken: String)
  private[connectors] case class VerifyCallbackUrlResponse(successful: Boolean)

  private[connectors] def fromAddCallbackUrlRequest(addCallbackUrlRequest: UpdateCallbackUrlRequest): VerifyCallbackUrlRequest =
    VerifyCallbackUrlRequest(addCallbackUrlRequest.callbackUrl, addCallbackUrlRequest.verifyToken)
}

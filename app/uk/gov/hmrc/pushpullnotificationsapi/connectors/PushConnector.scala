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
import play.api.http.Status.OK
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UnprocessableEntityException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models.ConnectorFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.OutboundNotification
import uk.gov.hmrc.pushpullnotificationsapi.models.{PushConnectorFailedResult, PushConnectorResult, PushConnectorSuccessResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class PushConnector @Inject()(http: HttpClient,
                              appConfig: AppConfig)
                             (implicit ec: ExecutionContext) {

  def send(pushNotificationRequest: OutboundNotification)(implicit hc: HeaderCarrier): Future[PushConnectorResult] = {
    doSend(pushNotificationRequest)
  }

  private def doSend(notification: OutboundNotification)(implicit hc: HeaderCarrier): Future[PushConnectorResult] = {
    val url = s"${appConfig.outboundNotificationsUrl}/notify"

    Logger.debug(s"Calling outbound notification gateway url=${notification.destinationUrl} \nheaders=${hc.headers} \npayload= ${notification.payload}")

    http.POST[OutboundNotification, HttpResponse](url, notification, hc.headers)
      .map(_.status).map[PushConnectorResult] {
        case OK => PushConnectorSuccessResult()
        case httpCode: Int =>
          Logger.info(s"Unexpected HTTP code $httpCode")
          PushConnectorFailedResult(new UnprocessableEntityException(s"Unexpected HTTP code $httpCode"))
      }
      .recover {
        case NonFatal(e) =>
          Logger.info("Call to ppns gateway failed:",e)
          PushConnectorFailedResult(e)
      }
  }
}

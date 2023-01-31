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

package uk.gov.hmrc.pushpullnotificationsapi.connectors

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import com.google.inject.Inject

import play.api.libs.json.Writes
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads}

import uk.gov.hmrc.pushpullnotificationsapi.models.ConnectorFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.MessageContentType.APPLICATION_JSON
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.OutboundConfirmation
import uk.gov.hmrc.pushpullnotificationsapi.models.{ConfirmationConnectorFailedResult, ConfirmationConnectorResult, ConfirmationConnectorSuccessResult}

@Singleton
class ConfirmationConnector @Inject() (http: HttpClient)(implicit ec: ExecutionContext) {

  def sendConfirmation(confirmationUrl: String, confirmation: OutboundConfirmation)(implicit hc: HeaderCarrier): Future[ConfirmationConnectorResult] = {
    doSend(confirmationUrl, confirmation, hc) map {
      _ => ConfirmationConnectorSuccessResult()
    } recover {
      case NonFatal(e) => ConfirmationConnectorFailedResult(e.getMessage)
    }
  }

  private def doSend[T, V](url: String, payload: T, hc: HeaderCarrier)(implicit wr: Writes[T], rd: HttpReads[V]): Future[V] = {
    implicit val modifiedHeaderCarrier: HeaderCarrier =
      hc.withExtraHeaders("Content-Type" -> APPLICATION_JSON.value)

    http.POST[T, V](url, payload)
  }
}

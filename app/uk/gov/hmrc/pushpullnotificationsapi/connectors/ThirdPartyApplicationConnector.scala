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

import java.util.UUID

import com.google.inject.Inject
import javax.inject.Singleton
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models.ClientId

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ThirdPartyApplicationConnector @Inject()(http: HttpClient, appConfig: AppConfig)(implicit ec: ExecutionContext) {

  implicit val formats = Json.format[ApplicationResponse]

  def getApplicationDetails(clientId: ClientId)(implicit hc: HeaderCarrier): Future[Option[ApplicationResponse]] = {

    val url = s"${appConfig.thirdPartyApplicationUrl}/application"
    
    http.GET[ApplicationResponse](url, Seq(("clientId", clientId.value))).map(Some(_)) recover {
      case t: Throwable => {
        Logger.warn(s"Unable to retrieve Application details for clientId [${clientId.value}]", t)
        None
      }
    }
  }
}

private[connectors] case class ApplicationResponse(id: UUID, clientId: String)

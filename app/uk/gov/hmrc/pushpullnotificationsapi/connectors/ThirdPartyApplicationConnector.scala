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

package uk.gov.hmrc.pushpullnotificationsapi.connectors

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

import com.google.inject.Inject

import play.api.libs.json.OFormat
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig

case class ApplicationResponse(id: ApplicationId)

object ApplicationResponse {
  import play.api.libs.json.Json

  implicit val format: OFormat[ApplicationResponse] = Json.format[ApplicationResponse]
}

@Singleton
class ThirdPartyApplicationConnector @Inject() (http: HttpClientV2, appConfig: AppConfig)(implicit ec: ExecutionContext) {

  def getApplicationDetails(clientId: ClientId)(implicit hc: HeaderCarrier): Future[ApplicationResponse] = {
    http.get(url"${appConfig.thirdPartyApplicationUrl}/application?${Seq("clientId" -> clientId)}").execute[ApplicationResponse]
  }
}

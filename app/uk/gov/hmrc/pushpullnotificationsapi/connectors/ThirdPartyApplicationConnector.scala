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
import com.google.inject.Inject
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models.ConnectorFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.ClientId

@Singleton
class ThirdPartyApplicationConnector @Inject() (http: HttpClient, appConfig: AppConfig)(implicit ec: ExecutionContext) {

  def getApplicationDetails(clientId: ClientId)(implicit hc: HeaderCarrier): Future[ApplicationResponse] = {
    val url = s"${appConfig.thirdPartyApplicationUrl}/application"
    http.GET[ApplicationResponse](url, Seq(("clientId", clientId.value)))
  }
}

case class ApplicationResponse(id: ApplicationId)

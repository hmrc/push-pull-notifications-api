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

import java.time.Clock
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import com.google.inject.Inject

import play.api.http.Status.CREATED
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, ApplicationId}
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.events.applications.domain.models.{ApplicationEvents, EventId}
import uk.gov.hmrc.apiplatform.modules.events.applications.domain.services.EventsInterServiceCallJsonFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models.Box
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

@Singleton
class ApiPlatformEventsConnector @Inject() (http: HttpClientV2, appConfig: AppConfig, val clock: Clock)(implicit ec: ExecutionContext) extends ApplicationLogger with ClockNow {

  def sendCallBackUpdatedEvent(applicationId: ApplicationId, oldUrl: String, newUrl: String, box: Box)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val event = ApplicationEvents.PpnsCallBackUriUpdatedEvent(EventId.random, applicationId, instant(), Actors.Unknown, box.boxId.value.toString, box.boxName, oldUrl, newUrl)
    http.post(url"${appConfig.apiPlatformEventsUrl}/application-events/ppnsCallbackUriUpdated")
      .withBody(Json.toJson(event))
      .execute[HttpResponse]
      .map(_.status == CREATED)
      .recoverWith {
        case NonFatal(e) =>
          logger.info("exception calling api platform events", e)
          Future.successful(false)
      }
  }
}

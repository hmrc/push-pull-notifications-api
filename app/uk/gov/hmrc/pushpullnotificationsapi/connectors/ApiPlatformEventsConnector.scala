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
import controllers.Assets.CREATED
import javax.inject.Singleton
import org.joda.time.DateTime
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.connectors.ApiPlatformEventsConnector.PpnsCallBackUriUpdatedEvent
import uk.gov.hmrc.pushpullnotificationsapi.models.ClientId

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApiPlatformEventsConnector @Inject()(http: HttpClient, appConfig: AppConfig)(implicit ec: ExecutionContext) {

  def sendEvent(clientId: ClientId, oldUrl: String, newUrl: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    //TODO - ideally we need the applicationId here..
    //Actor type and Actor id are hardcoded at present but eventually we will need to populate with
    // who made the change to the callback url
    val url = s"${appConfig.apiPlatformEventsUrl}/application-events/ppnsCallbackUriUpdated"
    val event = PpnsCallBackUriUpdatedEvent(clientId.value, DateTime.now(), oldUrl, newUrl)
    http.POST(url, event)
      .map(_.status == CREATED)
  }
}

object ApiPlatformEventsConnector{

  implicit val ppnsEventFormat: OFormat[PpnsCallBackUriUpdatedEvent] = Json.format[PpnsCallBackUriUpdatedEvent]
  private[connectors] case class Actor(){
    val actorId = ""
    val actorType = "UNKNOWN"
  }

  private[connectors] case class PpnsCallBackUriUpdatedEvent(applicationId: String,
  
                                                             eventDateTime: DateTime,
                                                             oldCallbackUrl: String,
                                                             newCallbackUrl: String) {
    val actor = Actor()
    val eventType = "PPNS_CALLBACK_URI_UPDATED"
  }


}



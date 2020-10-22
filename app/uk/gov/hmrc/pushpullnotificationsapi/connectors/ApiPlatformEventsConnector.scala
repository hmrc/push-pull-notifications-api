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
import uk.gov.hmrc.pushpullnotificationsapi.models.ApplicationId

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Reads
import play.api.libs.json.JodaReads
import play.api.libs.json.JodaWrites
import play.api.libs.json.Writes
import play.api.libs.json.Format

@Singleton
class ApiPlatformEventsConnector @Inject()(http: HttpClient, appConfig: AppConfig)(implicit ec: ExecutionContext) {
 
  val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
  implicit val JodaDateReads: Reads[org.joda.time.DateTime] = JodaReads.jodaDateReads(dateFormat)
  implicit val JodaDateWrites: Writes[org.joda.time.DateTime] = JodaWrites.jodaDateWrites(dateFormat)
  implicit val JodaDateTimeFormat: Format[DateTime] = Format(JodaDateReads, JodaDateWrites)
 
  implicit val ppnsEventFormat: OFormat[PpnsCallBackUriUpdatedEvent] = Json.format[PpnsCallBackUriUpdatedEvent]

  def sendEvent(applicationId: ApplicationId, oldUrl: String, newUrl: String)(implicit hc: HeaderCarrier): Future[Boolean] = {

    val url = s"${appConfig.apiPlatformEventsUrl}/application-events/ppnsCallbackUriUpdated"
    val event = PpnsCallBackUriUpdatedEvent(applicationId.value, DateTime.now(), oldUrl, newUrl)
    http.POST(url, event)
      .map(_.status == CREATED)
  }
}

object ApiPlatformEventsConnector{


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



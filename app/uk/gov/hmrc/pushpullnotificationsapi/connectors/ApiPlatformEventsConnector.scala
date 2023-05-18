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

import java.time.LocalDateTime
import java.util.UUID
import java.util.UUID.randomUUID
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import com.google.inject.Inject

import play.api.http.Status.CREATED
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.connectors.ApiPlatformEventsConnector.{EventId, PpnsCallBackUriUpdatedEvent}
import uk.gov.hmrc.pushpullnotificationsapi.models.{ApplicationId, Box}
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger
import uk.gov.hmrc.pushpullnotificationsapi.models.BoxId


object ApiPlatformEventsConnector {
  import play.api.libs.json._

  case class EventId(value: UUID) extends AnyVal

  object EventId {
    def random: EventId = EventId(randomUUID())

    implicit val format = Json.valueFormat[EventId]
  }

  //This is hardcoded at the moment as we dont have the details of the user who initiated the callback change
  case class Actor(actorType: String = "UNKNOWN")

  object Actor {
    implicit val actorFormat: Format[Actor] = Json.format[Actor]
  }

  case class PpnsCallBackUriUpdatedEvent(
      id: EventId,
      applicationId: ApplicationId,
      eventDateTime: LocalDateTime,
      oldCallbackUrl: String,
      newCallbackUrl: String,
      boxId: BoxId,
      boxName: String,
      actor: Actor = Actor()) {
    val eventType = "PPNS_CALLBACK_URI_UPDATED"
  }

  object PpnsCallBackUriUpdatedEvent {
      implicit val ppnsEventFormat: OFormat[PpnsCallBackUriUpdatedEvent] = Json.format[PpnsCallBackUriUpdatedEvent]
  }
}

@Singleton
class ApiPlatformEventsConnector @Inject() (http: HttpClient, appConfig: AppConfig)(implicit ec: ExecutionContext) extends ApplicationLogger {

  import ApiPlatformEventsConnector._

  def sendCallBackUpdatedEvent(applicationId: ApplicationId, oldUrl: String, newUrl: String, box: Box)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val url = s"${appConfig.apiPlatformEventsUrl}/application-events/ppnsCallbackUriUpdated"
    val event = PpnsCallBackUriUpdatedEvent(EventId.random, applicationId, LocalDateTime.now(), oldUrl, newUrl, box.boxId, box.boxName)
    http.POST[PpnsCallBackUriUpdatedEvent, HttpResponse](url, event)
      .map(_.status == CREATED)
      .recoverWith {
        case NonFatal(e) =>
          logger.info("exception calling api platform events", e)
          Future.successful(false)
      }
  }
}

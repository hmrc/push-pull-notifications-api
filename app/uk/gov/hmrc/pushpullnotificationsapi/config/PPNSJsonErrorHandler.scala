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

package uk.gov.hmrc.pushpullnotificationsapi.config

import javax.inject.Inject
import play.api.Configuration
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND}
import play.api.libs.json.Json.toJson
import play.api.mvc.Results.{BadRequest, NotFound, Status}
import play.api.mvc.{RequestHeader, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent
import uk.gov.hmrc.play.bootstrap.http.{ErrorResponse, JsonErrorHandler}
import uk.gov.hmrc.pushpullnotificationsapi.models.{ErrorCode, JsErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class PPNSJsonErrorHandler @Inject()(
                                      auditConnector: AuditConnector,
                                      httpAuditEvent: HttpAuditEvent,
                                      configuration: Configuration
                                    )(implicit ec: ExecutionContext)
  extends JsonErrorHandler(auditConnector, httpAuditEvent, configuration) {

  import httpAuditEvent.dataEvent

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    Future.successful {
      implicit val headerCarrier: HeaderCarrier = hc(request)
      statusCode match {
        case NOT_FOUND =>
          NotFound(JsErrorResponse(ErrorCode.NOT_FOUND, s"URI not found ${request.path}"))
        case BAD_REQUEST =>
          if (message.contains("Invalid Json")) {
            BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "JSON body is invalid against expected format"))
          } else {
            BadRequest(JsErrorResponse(ErrorCode.BAD_REQUEST, message))
          }
        case _ =>
          auditConnector.sendEvent(
            dataEvent(
              eventType = "ClientError",
              transactionName = s"A client error occurred, status: $statusCode",
              request = request,
              detail = Map.empty
            )
          )
          Status(statusCode)(toJson(ErrorResponse(statusCode, message)))
      }
    }

}

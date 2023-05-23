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

package uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.Results._
import play.api.mvc.{ActionRefiner, Request, Result}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions}
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import uk.gov.hmrc.pushpullnotificationsapi.models.{AuthenticatedNotificationRequest, ErrorCode, JsErrorResponse}
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

@Singleton
class AuthAction @Inject() (override val authConnector: AuthConnector)(implicit ec: ExecutionContext)
    extends ActionRefiner[Request, AuthenticatedNotificationRequest]
    with HttpErrorFunctions
    with AuthorisedFunctions
    with ApplicationLogger {
  actionName =>

  override def executionContext: ExecutionContext = ec

  override def refine[A](request: Request[A]): Future[Either[Result, AuthenticatedNotificationRequest[A]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    authorised().retrieve(uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.clientId) {
      maybeClientId: Option[String] =>
        maybeClientId match {
          case Some(clientId) => Future.successful(Right(AuthenticatedNotificationRequest[A](ClientId(clientId), request)))
          case _              =>
            logger.info("Unable to retrieve ClientId for request")
            Future.successful(Left(Unauthorized(JsErrorResponse(ErrorCode.UNAUTHORISED, "Unable to retrieve ClientId"))))
        }
    } recover {
      case e: AuthorisationException =>
        logger.info(s"Client Authorisation Failed with error: ${e.getMessage}")
        Left(Unauthorized(JsErrorResponse(ErrorCode.UNAUTHORISED, e.getMessage)))
    }
  }

}

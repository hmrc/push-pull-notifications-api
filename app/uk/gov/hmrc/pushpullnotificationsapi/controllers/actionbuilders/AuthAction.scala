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

package uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.{ActionRefiner, Request, Result}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions}
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions}
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.pushpullnotificationsapi.models.AuthenticatedNotificationRequest

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class AuthAction @Inject()(override val authConnector: AuthConnector)(implicit ec: ExecutionContext)
  extends ActionRefiner[Request, AuthenticatedNotificationRequest]  with HttpErrorFunctions with AuthorisedFunctions {
  actionName =>

  override def executionContext: ExecutionContext = ec
  override def refine[A](request: Request[A]):
  Future[Either[Result, AuthenticatedNotificationRequest[A]]]  = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers)

    authorised().retrieve(uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.clientId) {
      case maybeClientId: Option[String]  => maybeClientId match {
        case Some(clientId) =>  Future.successful(Right(AuthenticatedNotificationRequest[A](clientId, request)))
        case _ => Future.successful(Left(Unauthorized("unable to retrieve client id")))
      }
      case _ => Future.successful(Left(Unauthorized("Authorisation Failed")))
    } recover {
      case e: AuthorisationException =>Left(Unauthorized(Json.obj("errorMessage" -> e.getMessage)))
    }
  }


}

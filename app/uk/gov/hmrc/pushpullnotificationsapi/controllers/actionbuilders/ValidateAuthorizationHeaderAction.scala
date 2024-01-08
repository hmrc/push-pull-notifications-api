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

package uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.http.HeaderNames
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, Request, Result}
import uk.gov.hmrc.http.HttpErrorFunctions

import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models.{ErrorCode, JsErrorResponse}

@Singleton
class ValidateAuthorizationHeaderAction @Inject() (appConfig: AppConfig)(implicit ec: ExecutionContext) extends ActionFilter[Request] with HttpErrorFunctions {

  override def executionContext: ExecutionContext = ec

  override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
    val authHeader = request.headers.get(HeaderNames.AUTHORIZATION).getOrElse("")

    if (!authHeader.isEmpty && authHeader == appConfig.authorizationToken) Future.successful(None)
    else Future.successful(Some(Forbidden(JsErrorResponse(ErrorCode.FORBIDDEN, "Authorisation failed"))))

  }
}

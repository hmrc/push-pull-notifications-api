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

import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, Request, Result}
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.pushpullnotificationsapi.models.ErrorCode.INVALID_CONTENT_TYPE
import uk.gov.hmrc.pushpullnotificationsapi.models.{ErrorCode, JsErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ValidateContentTypeHeaderAction @Inject() (implicit ec: ExecutionContext) extends ActionFilter[Request] with HttpErrorFunctions {
  actionName =>

  override def executionContext: ExecutionContext = ec

  override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
    request.headers.get(CONTENT_TYPE) match {
      case Some("application/json") => successful(None)
      case _                        => successful(Some(NotAcceptable(JsErrorResponse(INVALID_CONTENT_TYPE, "The content type header is missing or invalid"))))
    }
  }
}

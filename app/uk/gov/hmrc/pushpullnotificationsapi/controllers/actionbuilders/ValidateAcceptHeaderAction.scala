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
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import play.api.http.HeaderNames.ACCEPT
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, Request, Result}

import uk.gov.hmrc.pushpullnotificationsapi.models.ErrorCode.ACCEPT_HEADER_INVALID
import uk.gov.hmrc.pushpullnotificationsapi.models.JsErrorResponse

@Singleton
class ValidateAcceptHeaderAction @Inject() (implicit ec: ExecutionContext) extends ActionFilter[Request] {

  override def executionContext: ExecutionContext = ec

  override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
    request.headers.get(ACCEPT) match {
      case Some("application/vnd.hmrc.1.0+json") => successful(None)
      case _                                     => successful(Some(NotAcceptable(JsErrorResponse(ACCEPT_HEADER_INVALID, "The accept header is missing or invalid"))))
    }
  }
}

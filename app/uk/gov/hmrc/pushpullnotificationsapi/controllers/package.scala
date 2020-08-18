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

package uk.gov.hmrc.pushpullnotificationsapi

import _root_.play.api.Logger
import _root_.play.api.libs.json.Json.toJson
import _root_.play.api.libs.json._
import _root_.play.api.mvc.Result
import _root_.play.api.mvc.Results._
import uk.gov.hmrc.http.{ForbiddenException, UnauthorizedException}
import uk.gov.hmrc.pushpullnotificationsapi.models.{ErrorCode, JsErrorResponse}

import scala.util.control.NonFatal

package object controllers {

  def recovery: PartialFunction[Throwable, Result] = {
    case NonFatal(e) =>
      Logger.error("An unexpected error occurred:", e)
      InternalServerError(JsErrorResponse(ErrorCode.UNKNOWN_ERROR, s"An unexpected error occurred:${e.getMessage}"))
  }
}

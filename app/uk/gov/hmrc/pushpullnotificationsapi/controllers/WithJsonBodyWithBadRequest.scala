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

package uk.gov.hmrc.pushpullnotificationsapi.controllers


import play.api.mvc.Request
import play.api.libs.json.Reads
import play.api.libs.json._
import play.api.mvc.Results.BadRequest
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import uk.gov.hmrc.play.bootstrap.controller.WithJsonBody
import scala.concurrent.Future
import uk.gov.hmrc.pushpullnotificationsapi.models.JsErrorResponse
import uk.gov.hmrc.pushpullnotificationsapi.models.ErrorCode
import play.api.mvc.Result

trait WithJsonBodyWithBadRequest {
  self: WithJsonBody =>

  override protected def withJsonBody[T](f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] = {
    withJson(request.body)(f)
  }

  protected def withJson[T](json: JsValue)(f: T => Future[Result])(implicit reads: Reads[T]): Future[Result] = {
    json.validate[T] match {
      case JsSuccess(payload, _) => f(payload)
      case JsError(_)            =>
        Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "JSON body is invalid against expected format")))
    }
  }

}
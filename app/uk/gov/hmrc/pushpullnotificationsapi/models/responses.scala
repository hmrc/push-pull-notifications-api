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

package uk.gov.hmrc.pushpullnotificationsapi.models

import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsObject, Json}

case class CreateBoxResponse(boxId: String)
case class CreateNotificationResponse(notificationId: String)

object ErrorCode extends Enumeration {
  type ErrorCode = Value

  val UNAUTHORISED = Value("UNAUTHORISED")
  val FORBIDDEN = Value("FORBIDDEN")
  val BOX_NOT_FOUND = Value("BOX_NOT_FOUND")
  val INVALID_REQUEST_PAYLOAD = Value("INVALID_REQUEST_PAYLOAD")
  val DUPLICATE_BOX = Value("DUPLICATE_BOX")
  val DUPLICATE_NOTIFICATION = Value("DUPLICATE_NOTIFICATION")
  val UNKNOWN_ERROR = Value("UNKNOWN_ERROR")
}

object JsErrorResponse {
  def apply(errorCode: ErrorCode.Value, message: JsValueWrapper): JsObject =
    Json.obj(
      "code" -> errorCode.toString,
      "message" -> message
    )
}
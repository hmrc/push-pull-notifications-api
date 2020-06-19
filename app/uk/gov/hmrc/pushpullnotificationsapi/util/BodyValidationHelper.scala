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

package uk.gov.hmrc.pushpullnotificationsapi.util

import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.pushpullnotificationsapi.models.{ErrorCode, JsErrorResponse, SubscriberId, SubscribersRequest, UpdateSubscribersRequest}

import scala.util.{Failure, Success, Try}

object BodyValidationHelper {


  def validateUpdateBoxSubscriberBody(updateSubscribersRequest: UpdateSubscribersRequest): Either[Result, UpdateSubscribersRequest] = {
    val results = updateSubscribersRequest.subscribers.map(validateBoxSubscriber).collect { case Left(x) => x }
    if (results.nonEmpty) Left(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "JSON body is invalid against expected format")))
    else Right(updateSubscribersRequest)
  }

  private def validateBoxSubscriber(request: SubscribersRequest): Either[Result, Option[SubscriberId]] = {
    for {result <- validateBoxSubscriberId(request)} yield result
  }

  private def validateBoxSubscriberId(request: SubscribersRequest): Either[Result, Option[SubscriberId]] = request.subscriberId.map { id =>
    Try[SubscriberId] {
      SubscriberId.fromString(id)
    } match {
      case Success(value) => Right(Some(value))
      case Failure(_) => Left(BadRequest(""))
    }
  } match {
    case Some(value) => value
    case None => Right(None)
  }
}

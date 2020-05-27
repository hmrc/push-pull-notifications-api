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
import play.api.mvc.Results._
import play.api.mvc.{ActionRefiner, Headers, Result}
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.pushpullnotificationsapi.models.{ValidatedNotificationHeaderRequest, ValidatedNotificationQueryRequest}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class ValidatedNotificationHeadersAction @Inject()(implicit ec: ExecutionContext)
  extends ActionRefiner[ValidatedNotificationQueryRequest, ValidatedNotificationHeaderRequest]  with HttpErrorFunctions {
  actionName =>

  override def executionContext: ExecutionContext = ec
  override def refine[A](request: ValidatedNotificationQueryRequest[A]): Future[Either[Result, ValidatedNotificationHeaderRequest[A]]]  = Future.successful {

    for {
      clientId <- validateHeaderAndExtractValue("X-CLIENT-ID", request.request.headers)
    } yield ValidatedNotificationHeaderRequest(clientId, request.params, request.request)
  }

  def validateHeaderAndExtractValue(headerName: String, headers: Headers): Either[Result, String] = {

    headers.get(headerName) match{
      case Some(x) => Right(x)
      case _ => Left(BadRequest)
    }
  }
}

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

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders.ValidateAuthorizationHeaderAction
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.services.ClientService

import scala.concurrent.ExecutionContext

@Singleton()
class ClientController @Inject() (
    validateAuthorizationHeaderAction: ValidateAuthorizationHeaderAction,
    clientService: ClientService,
    cc: ControllerComponents
  )(implicit val ec: ExecutionContext)
    extends BackendController(cc) {

  def getClientSecrets(clientId: ClientId): Action[AnyContent] = (Action andThen validateAuthorizationHeaderAction).async {
    clientService.getClientSecrets(clientId) map {
      case Some(clientSecrets) => Ok(Json.toJson(clientSecrets))
      case None                => NotFound(JsErrorResponse(ErrorCode.CLIENT_NOT_FOUND, "Client not found"))
    } recover recovery
  }
}

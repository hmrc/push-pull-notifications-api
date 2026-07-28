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

package uk.gov.hmrc.pushpullnotificationsapi.controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

import play.api.libs.json.*
import play.api.mvc.*
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders.ValidateUserAgentHeaderAction
import uk.gov.hmrc.pushpullnotificationsapi.models.*
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters.given
import uk.gov.hmrc.pushpullnotificationsapi.services.BoxService
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

@Singleton()
class BoxController @Inject() (
    validateUserAgentHeaderAction: ValidateUserAgentHeaderAction,
    boxService: BoxService,
    cc: ControllerComponents,
    playBodyParsers: PlayBodyParsers
  )(using ExecutionContext)
    extends BackendController(cc)
    with ApplicationLogger {

  def createBox(): Action[JsValue] =
    (Action andThen
      validateUserAgentHeaderAction)
      .async(playBodyParsers.json) { implicit request =>
        withJsonBody[CreateBoxRequest] {
          (box: CreateBoxRequest) =>
            if (box.boxName.isEmpty || box.clientId.value.isEmpty) {
              Future.successful(BadRequest(JsErrorResponse(ErrorCode.InvalidRequestPayload, "Expecting boxName and clientId in request body")))
            } else {
              boxService.createBox(box.clientId, box.boxName).map {
                case r: BoxCreatedResult      => Created(Json.toJson(CreateBoxResponse(r.box.boxId)))
                case r: BoxRetrievedResult    =>
                  Ok(Json.toJson(CreateBoxResponse(r.box.boxId)))
                case r: BoxCreateFailedResult =>
                  logger.error(s"Unable to create Box: ${r.message}")
                  UnprocessableEntity(JsErrorResponse(ErrorCode.UnknownError, s"unable to createBox:${r.message}"))
              }
            }
        } recover recovery
      }

  def getBoxes(boxName: Option[String], clientId: Option[ClientId]): Action[AnyContent] = Action.async {
    ((boxName, clientId) match {
      case (Some(boxName), Some(clientId)) => getBoxByNameAndClientId(boxName, clientId)
      case (None, None)                    => boxService.getAllBoxes().map(boxes => Ok(Json.toJson(boxes)))
      case _ => Future.successful(BadRequest(JsErrorResponse(ErrorCode.BadRequest, s"Must specify both boxName and clientId query parameters or neither")))
    }) recover recovery
  }

  private def getBoxByNameAndClientId(boxName: String, clientId: ClientId): Future[Result] = {
    boxService.getBoxByNameAndClientId(boxName, clientId) map {
      case Some(box) => Ok(Json.toJson(box))
      case None      => NotFound(JsErrorResponse(ErrorCode.BoxNotFound, "Box not found"))
    }
  }

  def updateCallbackUrl(boxId: BoxId): Action[JsValue] =
    (Action andThen
      validateUserAgentHeaderAction)
      .async(playBodyParsers.json) { implicit request =>
        withJsonBody[UpdateCallbackUrlRequest] { addCallbackUrlRequest =>
          if (addCallbackUrlRequest.isInvalid) {
            Future.successful(BadRequest(JsErrorResponse(ErrorCode.InvalidRequestPayload, "clientId is required")))
          } else {
            boxService.updateCallbackUrl(boxId, addCallbackUrlRequest) map {
              case _: CallbackUrlUpdated                  => Ok(Json.toJson(UpdateCallbackUrlResponse(successful = true)))
              case c: CallbackValidationFailed            => Ok(Json.toJson(UpdateCallbackUrlResponse(successful = false, Some(c.errorMessage))))
              case u: UnableToUpdateCallbackUrl           => Ok(Json.toJson(UpdateCallbackUrlResponse(successful = false, Some(u.errorMessage))))
              case _: BoxIdNotFound                       => NotFound(JsErrorResponse(ErrorCode.BoxNotFound, "Box not found"))
              case _: UpdateCallbackUrlUnauthorisedResult => Unauthorized(JsErrorResponse(ErrorCode.Unauthorised, "Client Id did not match"))
            } recover recovery
          }
        }
      }

  override protected def withJsonBody[T](f: T => Future[Result])(using request: Request[JsValue], ct: ClassTag[T], reads: Reads[T]): Future[Result] =
    withJson(request.body)(f)

  private def withJson[T](json: JsValue)(f: T => Future[Result])(using reads: Reads[T]): Future[Result] = {
    json.validate[T] match {
      case JsSuccess(payload, _) => f(payload)
      case JsError(_)            =>
        Future.successful(BadRequest(JsErrorResponse(ErrorCode.InvalidRequestPayload, "JSON body is invalid against expected format")))
    }
  }
}

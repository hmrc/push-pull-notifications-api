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

package uk.gov.hmrc.pushpullnotificationsapi.controllers

import java.util.UUID

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders.ValidateUserAgentHeaderAction
import uk.gov.hmrc.pushpullnotificationsapi.models.RequestFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.services.BoxService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton()
class BoxController @Inject()(validateUserAgentHeaderAction: ValidateUserAgentHeaderAction,
                              boxService: BoxService,
                              cc: ControllerComponents,
                              playBodyParsers: PlayBodyParsers)
                             (implicit val ec: ExecutionContext) extends BackendController(cc) {

  def createBox(): Action[JsValue] =
    (Action andThen
      validateUserAgentHeaderAction)
      .async(playBodyParsers.json) { implicit request =>
        withJsonBody[CreateBoxRequest] {
          box: CreateBoxRequest =>
            if (box.boxName.isEmpty || box.clientId.isEmpty) {
              Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Expecting boxName and clientId in request body")))
            } else {
              val boxId = BoxId(UUID.randomUUID())
              boxService.createBox(boxId, ClientId(box.clientId), box.boxName).map {
                case r: BoxCreatedResult => Created(Json.toJson(CreateBoxResponse(r.boxId.raw)))
                case r: BoxRetrievedResult => Ok(Json.toJson(CreateBoxResponse(r.boxId.raw)))
                case r: BoxCreateFailedResult =>
                  Logger.info(s"Unable to create Box: ${r.message}")
                  UnprocessableEntity(JsErrorResponse(ErrorCode.UNKNOWN_ERROR, s"unable to createBox:${r.message}"))
              }
            }
        } recover recovery
      }

  def getBoxByNameAndClientId(boxName: String, clientId: ClientId): Action[AnyContent] = Action.async {
    boxService.getBoxByNameAndClientId(boxName, clientId) map {
      case List(box) => Ok(Json.toJson(box))
      case _ => NotFound(JsErrorResponse(ErrorCode.BOX_NOT_FOUND, "Box not found"))
    } recover recovery
  }

  def updateSubscriber(boxId: BoxId): Action[JsValue] = Action.async(playBodyParsers.json) { implicit request =>
    withJsonBody[UpdateSubscriberRequest] { updateSubscriberRequest =>
      boxService.updateSubscriber(boxId, updateSubscriberRequest) map {
        case Some(box) => Ok(Json.toJson(box))
        case _ => Logger.info("box not found or update failed")
          NotFound(JsErrorResponse(ErrorCode.BOX_NOT_FOUND, "Box not found"))
      } recover recovery
    }
  }

  def updateCallbackUrl(boxId: BoxId): Action[JsValue] = Action.async(playBodyParsers.json) { implicit request =>
    withJsonBody[UpdateCallbackUrlRequest] { addCallbackUrlRequest =>
      if (addCallbackUrlRequest.clientId.value.isEmpty || addCallbackUrlRequest.callbackUrl.isEmpty || addCallbackUrlRequest.verifyToken.isEmpty) {
        Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "clientId, callbackUrl and verifyToken properties are all required")))
      } else {
        boxService.updateCallbackUrl(boxId, addCallbackUrlRequest) map {
          case CallbackUrlUpdated() => NoContent
          case BoxIdNotFound() => NotFound(JsErrorResponse(ErrorCode.BOX_NOT_FOUND, "Box not found"))
          case UnableToUpdateCallbackUrl() => BadRequest(JsErrorResponse(ErrorCode.UNKNOWN_ERROR, "Unable to update Callback URL"))
        } recover recovery
      }
    }
  }

  private def recovery: PartialFunction[Throwable, Result] = {
    case NonFatal(e) =>
      Logger.error("An unexpected error occurred:", e)
      InternalServerError(JsErrorResponse(ErrorCode.UNKNOWN_ERROR, s"An unexpected error occurred:${e.getMessage}"))
  }

  override protected def withJsonBody[T]
  (f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result]
  = {
    withJson(request.body)(f)
  }

  private def withJson[T](json: JsValue)(f: T => Future[Result])(implicit reads: Reads[T]): Future[Result] = {
    json.validate[T] match {
      case JsSuccess(payload, _) => f(payload)
      case JsError(errs) =>
        Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "JSON body is invalid against expected format")))
    }
  }
}


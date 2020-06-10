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
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders.ValidateUserAgentHeaderAction
import uk.gov.hmrc.pushpullnotificationsapi.models.RequestFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.services.TopicsService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

@Singleton()
class TopicsController @Inject()(appConfig: AppConfig,
                                 validateUserAgentHeaderAction: ValidateUserAgentHeaderAction,
                                 topicsService: TopicsService,
                                 cc: ControllerComponents,
                                 playBodyParsers: PlayBodyParsers)
                                (implicit val ec: ExecutionContext) extends BackendController(cc) {

  def createTopic(): Action[JsValue] =
    (Action andThen
      validateUserAgentHeaderAction)
      .async(playBodyParsers.json) { implicit request =>
        withJsonBody[CreateTopicRequest] {
          topic: CreateTopicRequest =>
            val topicId = TopicId(UUID.randomUUID())
            topicsService.createTopic(topicId, ClientId(topic.clientId), topic.topicName).map {
              case Right(result: TopicServiceCreateSuccessResult) => Created(Json.toJson(CreateTopicResponse(result.topicId.raw)))
              case Right(result: TopicServiceCreateRetrievedSuccessResult) => Ok(Json.toJson(CreateTopicResponse(result.topicId.raw)))
              case Left(x: TopicServiceCreateFailedResult) =>
                UnprocessableEntity(JsErrorResponse(ErrorCode.UNKNOWN_ERROR, s"unable to createTopic:${x.message}"))
            }
        } recover recovery
      }

  def getTopicByNameAndClientId(topicName: String, clientId: ClientId): Action[AnyContent] = Action.async {
    topicsService.getTopicByNameAndClientId(topicName, clientId) map {
      case List(topic) => Ok(Json.toJson(topic))
      case _ => NotFound
    } recover recovery
  }

  def updateSubscribers(topicId: TopicId): Action[JsValue] = Action.async(playBodyParsers.json) { implicit request =>
    withJsonBody[UpdateSubscribersRequest] {
      updateRequest =>
        topicsService.updateSubscribers(topicId, updateRequest) map {
          case Some(topic) => Ok(Json.toJson(topic))
          case _ => Logger.info("topic not found or update failed")
            NotFound
        } recover recovery
    }
  }

  private def recovery: PartialFunction[Throwable, Result] = {
    case NonFatal(e) =>
      Logger.info("An unexpected error occurred:", e)
      e match {
        case _ => InternalServerError(JsErrorResponse(ErrorCode.UNKNOWN_ERROR, s"An unexpected error occurred:${e.getMessage}"))
      }
  }


  override protected def withJsonBody[T]
  (f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result]

  = {
    withJson(request.body)(f)
  }

  private def withJson[T](json: JsValue)(f: T => Future[Result])(implicit reads: Reads[T]): Future[Result] = {
    Try(json.validate[T]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs)) =>
        Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, JsError.toJson(errs))))
      case Failure(e) =>
        Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, e.getMessage)))
    }
  }
}


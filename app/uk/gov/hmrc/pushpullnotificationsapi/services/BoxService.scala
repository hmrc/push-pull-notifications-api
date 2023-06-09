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

package uk.gov.hmrc.pushpullnotificationsapi.services

import java.{util => ju}
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.pushpullnotificationsapi.connectors.{ApiPlatformEventsConnector, PushConnector, ThirdPartyApplicationConnector}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

@Singleton
class BoxService @Inject() (
    repository: BoxRepository,
    pushConnector: PushConnector,
    applicationConnector: ThirdPartyApplicationConnector,
    eventsConnector: ApiPlatformEventsConnector,
    clientService: ClientService
  )(implicit ec: ExecutionContext)
    extends ApplicationLogger {

  def createBox(clientId: ClientId, boxName: String, clientManaged: Boolean = false)(implicit hc: HeaderCarrier): Future[CreateBoxResult] = {

    repository.getBoxByNameAndClientId(boxName, clientId) flatMap {
      case Some(x) => successful(BoxRetrievedResult(x))
      case _       =>
        for {
          _ <- clientService.findOrCreateClient(clientId)
          appDetails <- applicationConnector.getApplicationDetails(clientId)
          createdBox <- repository.createBox(Box(BoxId(ju.UUID.randomUUID), boxName, BoxCreator(clientId), Some(appDetails.id), None, clientManaged))
        } yield createdBox
    } recoverWith {
      case NonFatal(e) => successful(BoxCreateFailedResult(e.getMessage))
    }
  }

  def getAllBoxes(): Future[List[Box]] = {
    repository.getAllBoxes()
  }

  def deleteBox(clientId: ClientId, boxId: BoxId): Future[DeleteBoxResult] = {
    repository.findByBoxId(boxId) flatMap {
      case Some(box) => validateAndDeleteBox(box, clientId)
      case None      => successful(BoxDeleteNotFoundResult())
    }
  }

  def getBoxByNameAndClientId(boxName: String, clientId: ClientId): Future[Option[Box]] =
    repository.getBoxByNameAndClientId(boxName, clientId)

  def getBoxesByClientId(clientId: ClientId): Future[List[Box]] =
    repository.getBoxesByClientId(clientId)

  def updateCallbackUrl(
      boxId: BoxId,
      request: UpdateCallbackUrlRequest,
      clientManaged: Boolean
    )(implicit ec: ExecutionContext,
      hc: HeaderCarrier
    ): Future[UpdateCallbackUrlResult] = {
    repository.findByBoxId(boxId) flatMap {
      case Some(box) => if (box.boxCreator.clientId == request.clientId && box.clientManaged == clientManaged) {
          val oldUrl: String = box.subscriber.map(extractCallBackUrl).getOrElse("")

          for {
            appId <- if (box.applicationId.isEmpty) updateBoxWithApplicationId(box) else successful(box.applicationId.get)
            result <- validateCallBack(box, request)
            _ = result match {
                  case successfulUpdate: CallbackUrlUpdated =>
                    eventsConnector.sendCallBackUpdatedEvent(appId, oldUrl, request.callbackUrl, box) recoverWith {
                      case NonFatal(e) => logger.warn(s"Unable to send CallbackUrlUpdated event", e)
                        successful(successfulUpdate)
                    }
                  case _                                    => logger.warn("Updating callback URL failed - not sending event")
                }
          } yield result
        } else successful(UpdateCallbackUrlUnauthorisedResult())
      case None      => successful(BoxIdNotFound())
    } recoverWith {

      case NonFatal(e) => successful(UnableToUpdateCallbackUrl(errorMessage = e.getMessage))
    }

  }

  def validateBoxOwner(boxId: BoxId, clientId: ClientId): Future[ValidateBoxOwnerResult] = {
    repository.findByBoxId(boxId) flatMap {
      case None      => Future.successful(ValidateBoxOwnerNotFoundResult(s"BoxId: ${boxId.value.toString} not found"))
      case Some(box) => if (box.boxCreator.clientId == clientId) {
          Future.successful(ValidateBoxOwnerSuccessResult())
        } else {
          Future.successful(ValidateBoxOwnerFailedResult("clientId does not match boxCreator"))
        }
    }
  }

  private def validateCallBack(box: Box, request: UpdateCallbackUrlRequest): Future[UpdateCallbackUrlResult] = {
    if (request.callbackUrl.nonEmpty) {
      pushConnector.validateCallbackUrl(request) flatMap {
        case _: PushConnectorSuccessResult     =>
          logger.info(s"Callback Validated for boxId:${box.boxId.value} updating push callbackUrl")
          updateBoxWithCallBack(box.boxId, new SubscriberContainer(PushSubscriber(request.callbackUrl)))
        case result: PushConnectorFailedResult =>
          logger.info(s"Callback validation failed for boxId:${box.boxId.value}")
          successful(CallbackValidationFailed(result.errorMessage))
      }
    } else {
      logger.info(s"updating callback for boxId:${box.boxId.value} with PullSubscriber")
      updateBoxWithCallBack(box.boxId, new SubscriberContainer(PullSubscriber("")))
    }
  }

  private def updateBoxWithCallBack(boxId: BoxId, subscriber: SubscriberContainer[Subscriber]): Future[UpdateCallbackUrlResult] =
    repository.updateSubscriber(boxId, subscriber).map {
      case Some(_) => CallbackUrlUpdated()
      case _       => UnableToUpdateCallbackUrl(errorMessage = "Unable to update box with callback Url")
    }

  private def extractCallBackUrl(subscriber: Subscriber): String = {
    subscriber match {
      case x: PushSubscriber => x.callBackUrl
      case _                 => ""
    }
  }

  private def updateBoxWithApplicationId(box: Box)(implicit hc: HeaderCarrier): Future[ApplicationId] = {
    applicationConnector.getApplicationDetails(box.boxCreator.clientId)
      .flatMap(appDetails => {
        repository.updateApplicationId(box.boxId, appDetails.id)
        successful(appDetails.id)
      })
  }

  private def validateAndDeleteBox(box: Box, clientId: ClientId): Future[DeleteBoxResult] = {
    if (!box.clientManaged || box.boxCreator.clientId != clientId) {
      successful(BoxDeleteAccessDeniedResult())
    } else if (box.boxCreator.clientId == clientId) {
      repository.deleteBox(box.boxId)
    } else {
      successful(BoxDeleteNotFoundResult())
    }
  }

}

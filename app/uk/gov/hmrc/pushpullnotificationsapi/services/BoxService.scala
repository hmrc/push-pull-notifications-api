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

package uk.gov.hmrc.pushpullnotificationsapi.services

import java.{util => ju}
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.pushpullnotificationsapi.connectors.{ApiPlatformEventsConnector, ThirdPartyApplicationConnector}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository
import uk.gov.hmrc.pushpullnotificationsapi.services.PushService
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

@Singleton
class BoxService @Inject() (
    repository: BoxRepository,
    pushService: PushService,
    applicationConnector: ThirdPartyApplicationConnector,
    eventsConnector: ApiPlatformEventsConnector,
    clientService: ClientService
  )(implicit ec: ExecutionContext)
    extends ApplicationLogger {

  def createBox(clientId: ClientId, boxName: String)(implicit hc: HeaderCarrier): Future[CreateBoxResult] = {

    repository.getBoxByNameAndClientId(boxName, clientId) flatMap {
      case Some(x) => successful(BoxRetrievedResult(x))
      case _       =>
        for {
          _ <- clientService.findOrCreateClient(clientId)
          appDetails <- applicationConnector.getApplicationDetails(clientId)
          createdBox <- repository.createBox(Box(BoxId(ju.UUID.randomUUID), boxName, BoxCreator(clientId), Some(appDetails.id), None))
        } yield createdBox
    } recoverWith {
      case NonFatal(e) => successful(BoxCreateFailedResult(e.getMessage))
    }
  }

  def getAllBoxes(): Future[List[Box]] = {
    repository.getAllBoxes()
  }

  def getBoxByNameAndClientId(boxName: String, clientId: ClientId): Future[Option[Box]] =
    repository.getBoxByNameAndClientId(boxName, clientId)

  def updateCallbackUrl(
      boxId: BoxId,
      request: UpdateCallbackUrlRequest
    )(implicit ec: ExecutionContext,
      hc: HeaderCarrier
    ): Future[UpdateCallbackUrlResult] = {
    repository.findByBoxId(boxId).flatMap {

      // Perhaps a case for pattern matching extract of values ? but see comment below.  To revist at a later date.

      case Some(box) => if (box.boxCreator.clientId == request.clientId) {
          val oldUrl: String = box.subscriber.map(extractCallBackUrl).getOrElse("")

          // Issues with the type (wrapped in future) and with the lack of mapping - is sending the event meant to be fire and forget?
          // Caution wins over reworking this.
          for {
            appId <- box.applicationId.fold(updateBoxWithApplicationId(box))(id => successful(id))
            result <- validateCallBack(box, request)
            _ = result match {
                  case successfulUpdate: CallbackUrlUpdated =>
                    eventsConnector.sendCallBackUpdatedEvent(appId, oldUrl, request.callbackUrl, box).recoverWith {
                      case NonFatal(e) =>
                        logger.warn(s"Unable to send CallbackUrlUpdated event", e)
                        successful(successfulUpdate)
                    }
                  case _                                    => logger.warn("Updating callback URL failed - not sending event")
                }
          } yield result
        } else successful(UpdateCallbackUrlUnauthorisedResult())

      case None => successful(BoxIdNotFound())
    } recoverWith {
      case NonFatal(e) => successful(UnableToUpdateCallbackUrl(errorMessage = e.getMessage))
    }
  }

  private def validateCallBack(box: Box, request: UpdateCallbackUrlRequest): Future[UpdateCallbackUrlResult] = {
    if (request.callbackUrl.nonEmpty) {
      pushService.validateCallbackUrl(request) flatMap {
        case _: PushServiceSuccessResult     =>
          logger.info(s"Callback Validated for boxId:${box.boxId} updating push callbackUrl")
          updateBoxWithCallBack(box.boxId, new SubscriberContainer(PushSubscriber(request.callbackUrl)))
        case result: PushServiceFailedResult =>
          logger.info(s"Callback validation failed for boxId:${box.boxId}")
          successful(CallbackValidationFailed(result.errorMessage))
      }
    } else {
      logger.info(s"updating callback for boxId:${box.boxId} with PullSubscriber")
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

}

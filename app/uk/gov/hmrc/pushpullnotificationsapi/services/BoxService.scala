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

package uk.gov.hmrc.pushpullnotificationsapi.services

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pushpullnotificationsapi.connectors.{PushConnector, ThirdPartyApplicationConnector}
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import java.{util => ju}
import scala.util.control.NonFatal
import uk.gov.hmrc.pushpullnotificationsapi.connectors.ApiPlatformEventsConnector


@Singleton
class BoxService @Inject()(repository: BoxRepository,
                           pushConnector: PushConnector,
                           applicationConnector: ThirdPartyApplicationConnector,
                           eventsConnecter: ApiPlatformEventsConnector,
                           clientService: ClientService) {

  def createBox(clientId: ClientId, boxName: String)
               (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[CreateBoxResult] = {

    repository.getBoxByNameAndClientId(boxName, clientId) flatMap {
      case Some(x) => successful(BoxRetrievedResult(x))
      case _ =>
        for {
          _ <- clientService.findOrCreateClient(clientId)
          appDetails <- applicationConnector.getApplicationDetails(clientId)
          createdBox <- repository.createBox(Box(BoxId(ju.UUID.randomUUID), boxName, BoxCreator(clientId), Some(appDetails.id)))
        } yield createdBox
    } recoverWith {
      case NonFatal(e) => successful(BoxCreateFailedResult(e.getMessage()))
    }
  }

  // def updateBoxWithApplicationIdIfMissing(boxId: BoxId)
  //                                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[Box]] ={
  //   for {
  //     maybeBox <- repository.findByBoxId(boxId)
  //     maybeHasApplicationId: Option[Boolean] = maybeBox.map(_.applicationId.isDefined)
  //     maybeUpdatedBox <- (maybeBox, maybeHasApplicationId) match {
  //                       case (Some(box), Some(false)) => updateBoxWithApplicationId(box)
  //                       case _  =>   successful(maybeBox)
  //                     }
  //   } yield maybeUpdatedBox
  // }


  def getBoxByNameAndClientId(boxName: String, clientId: ClientId)(implicit ec: ExecutionContext): Future[Option[Box]] =
    repository.getBoxByNameAndClientId(boxName, clientId)

  @deprecated("No longer supported use updateCallbackUrl", since = "0.88.x")
  def updateSubscriber(boxId: BoxId, request: UpdateSubscriberRequest)
                      (implicit ec: ExecutionContext): Future[Option[Box]] = {
    val subscriber = request.subscriber

    val subscriberObject = subscriber.subscriberType match {
      case API_PULL_SUBSCRIBER => PullSubscriber(callBackUrl = subscriber.callBackUrl)
      case API_PUSH_SUBSCRIBER => PushSubscriber(callBackUrl = subscriber.callBackUrl)
    }

    repository.updateSubscriber(boxId, new SubscriberContainer(subscriberObject))
  }

  def updateCallbackUrl(boxId: BoxId, request: UpdateCallbackUrlRequest)
                       (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[UpdateCallbackUrlResult] = {
    repository.findByBoxId(boxId) flatMap {
      case Some(box) => if (box.boxCreator.clientId.equals(request.clientId)) {
        for {
          appId <- if (box.applicationId.isEmpty) updateBoxWithApplicationId(box) else successful(box.applicationId.get)
          result <- validateCallBack(box, appId, request)
        } yield result
      }
      else successful(UpdateCallbackUrlUnauthorisedResult())
      case None => successful(BoxIdNotFound())
    } recoverWith {
      case NonFatal(e) => successful(UnableToUpdateCallbackUrl(errorMessage = e.getMessage()))
    }

  }

  private def validateCallBack(box: Box, appId: ApplicationId, request: UpdateCallbackUrlRequest)
                              (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[UpdateCallbackUrlResult] = {
     val oldUrl: String = box.subscriber.map(extractCallBackUrl(_)).getOrElse("")

    if (!request.callbackUrl.isEmpty) {
      pushConnector.validateCallbackUrl(request) flatMap {
        case _: PushConnectorSuccessResult => {
          Logger.info(s"Callback Validated for boxId:${box.boxId.value} updating push callbackUrl")
          updateBoxWithCallBack(box.boxId, new SubscriberContainer(PushSubscriber(request.callbackUrl))).map(
            result => {
              eventsConnecter.sendEvent(appId, oldUrl, request.callbackUrl)
              result
            })
        }
        case result: PushConnectorFailedResult => {
          Logger.info(s"Callback validation failed for boxId:${box.boxId.value}")
          successful(CallbackValidationFailed(result.errorMessage))
        }
      }
    } else {
      Logger.info(s"updating callback for boxId:${box.boxId.value} with PullSubscriber")
      updateBoxWithCallBack(box.boxId, new SubscriberContainer(PullSubscriber("")))
    }
  }

  private def updateBoxWithCallBack(boxId: BoxId, subscriber: SubscriberContainer[Subscriber])
                                   (implicit ec: ExecutionContext): Future[UpdateCallbackUrlResult] =                                                              
    repository.updateSubscriber(boxId, subscriber).map {
      case Some(_) => CallbackUrlUpdated() 
      case _ => UnableToUpdateCallbackUrl(errorMessage = "Unable to update box with callback Url")
    }
  

  private def extractCallBackUrl(subscriber: Subscriber): String = {
   subscriber match {case x: PushSubscriber => x.callBackUrl
                     case _ => ""
                     }
  }

  private def updateBoxWithApplicationId(box: Box)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[ApplicationId] = {
    applicationConnector.getApplicationDetails(box.boxCreator.clientId)
      .flatMap(appDetails => {
        repository.updateApplicationId(box.boxId, appDetails.id)
        successful(appDetails.id)
      })
  }


}

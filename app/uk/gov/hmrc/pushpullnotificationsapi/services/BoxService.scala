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

@Singleton
class BoxService @Inject()(repository: BoxRepository,
                           pushConnector: PushConnector,
                           applicationConnector: ThirdPartyApplicationConnector,
                           clientService: ClientService) {

  def createBox(clientId: ClientId, boxName: String)
               (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[BoxCreateResult] = {

    repository.getBoxByNameAndClientId(boxName, clientId) flatMap {
      case Some(x) => successful(BoxRetrievedResult(x.boxId))
      case _ =>
        for {
          _ <- clientService.findOrCreateClient(clientId)
          maybeApplicationDetails <- applicationConnector.getApplicationDetails(clientId)
          createdBox: Box <- repository.createBox(Box(BoxId(ju.UUID.randomUUID), boxName, BoxCreator(clientId), maybeApplicationDetails.map(_.id)))
        } yield BoxCreatedResult(createdBox.boxId)
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
      case Some(box) => if (box.boxCreator.clientId.equals(request.clientId)){
          // if unable to update app id or app id is empty fail
           validateCallBack(box, request)
      }
      else successful(UpdateCallbackUrlUnauthorisedResult())
      case None => successful(BoxIdNotFound())
    }

  }

  private def validateCallBack(box: Box, request: UpdateCallbackUrlRequest)
  (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[UpdateCallbackUrlResult] = {
  
    if (!request.callbackUrl.isEmpty) {
      pushConnector.validateCallbackUrl(request) flatMap {
        case _: PushConnectorSuccessResult => {
          Logger.info(s"Callback Validated for boxId:${box.boxId.value} updating push callbackUrl")
          updateBoxWithCallBack(box, new SubscriberContainer(PushSubscriber(request.callbackUrl)))
        }
        case result: PushConnectorFailedResult => {
          Logger.info(s"Callback validation failed for boxId:${box.boxId.value}")
          successful(CallbackValidationFailed(result.errorMessage))
        }
      }
    } else {
      Logger.info(s"updating callback for boxId:${box.boxId.value} with PullSubscriber")
      updateBoxWithCallBack(box, new SubscriberContainer(PullSubscriber("")))
    }
  }

  private def updateBoxWithCallBack(box: Box, subscriber: SubscriberContainer[Subscriber])
                                   (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[UpdateCallbackUrlResult] = {
    for{
      maybeApplicationId <- if(box.applicationId.isEmpty) updateBoxWithApplicationId(box) else successful(false)
      updateResult  <-      repository.updateSubscriber(box.boxId, subscriber).map {
                                case Some(_) => { 

                                  //if(maybeApplicationId.isDefined) sendURLUPDATEDEVENT
                                  CallbackUrlUpdated()
                                }
                                case _ => {
                                  UnableToUpdateCallbackUrl(errorMessage = "Unable to update box with callback Url")
                                }
                            }
    } yield updateResult
}

    private def updateBoxWithApplicationId(box:Box)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[ApplicationId]] = {
      for {
        maybeResponse <- applicationConnector.getApplicationDetails(box.boxCreator.clientId)
        appId <- maybeResponse.map(_.id) match {
            case Some(applicationId) => repository.updateApplicationId(box.boxId, applicationId).map(_ => Some(applicationId))
            case _ => successful(None)
        }
      } yield appId
    }

}

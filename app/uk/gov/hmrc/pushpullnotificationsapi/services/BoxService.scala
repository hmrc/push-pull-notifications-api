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
import uk.gov.hmrc.pushpullnotificationsapi.connectors.PushConnector
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.{BoxRepository, ClientRepository}

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BoxService @Inject()(repository: BoxRepository,
                           pushConnector: PushConnector,
                           clientRepository: ClientRepository,
                           clientSecretGenerator: ClientSecretGenerator) {

  def createBox(boxId: BoxId, clientId: ClientId, boxName: String)
               (implicit ec: ExecutionContext): Future[BoxCreateResult] = {

    for {
      client: Option[Client] <- clientRepository.findByClientId(clientId)
      _ <- client.fold(clientRepository.insertClient(Client(clientId, Seq(clientSecretGenerator.generate))))(successful)
      boxId: Option[BoxId] <- repository.createBox(Box(boxId, boxName, BoxCreator(clientId)))
      boxCreateResult: BoxCreateResult <- boxId match {
        case Some(id) => successful(BoxCreatedResult(id))
        case None => repository.getBoxByNameAndClientId(boxName, clientId) map {
          case Some(x) => BoxRetrievedResult(x.boxId)
          case _ =>
            Logger.info(s"Box with name :$boxName already exists for clientId: $clientId but unable to retrieve")
            BoxCreateFailedResult(s"Box with name :$boxName already exists for this clientId but unable to retrieve it")
        }
      }
    } yield boxCreateResult
  }

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

  def updateCallbackUrl(boxId: BoxId, request: UpdateCallbackUrlRequest)(implicit ec: ExecutionContext): Future[UpdateCallbackUrlResult] = {
    repository.findByBoxId(boxId) flatMap {
      case Some(box) => if (box.boxCreator.clientId.equals(request.clientId)) validateCallBack(boxId, request)
      else successful(UpdateCallbackUrlUnauthorisedResult())
      case None => successful(BoxIdNotFound())
    }

  }

  private def validateCallBack(boxId: BoxId, request: UpdateCallbackUrlRequest)(implicit ec: ExecutionContext): Future[UpdateCallbackUrlResult] = {
    if (!request.callbackUrl.isEmpty) {
      pushConnector.validateCallbackUrl(request) flatMap {
        case _: PushConnectorSuccessResult => {
          Logger.info("Callback Validated for boxId:${boxId.value} updating push callbackUrl")
          updateBoxWithCallBack(boxId, new SubscriberContainer(PushSubscriber(request.callbackUrl)))
        }
        case result: PushConnectorFailedResult => {
          Logger.info("Callback validation failed for boxId:${boxId.value}")
          successful(CallbackValidationFailed(result.errorMessage))
        }
      }
    } else {
      Logger.info(s"updating callback for boxId:${boxId.value} with PullSubscriber")
      updateBoxWithCallBack(boxId, new SubscriberContainer(PullSubscriber("")))
    }
  }

  private def updateBoxWithCallBack(boxId: BoxId, subscriber: SubscriberContainer[Subscriber])
                                   (implicit ec: ExecutionContext): Future[UpdateCallbackUrlResult] = {
    repository.updateSubscriber(boxId, subscriber).map {
      case Some(_) => CallbackUrlUpdated()
      case _ => {
        UnableToUpdateCallbackUrl(errorMessage = "Unable to update box with callback Url")
      }
    }
  }

}

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
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType._
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BoxService @Inject()(repository: BoxRepository) {

  def createBox(boxId: BoxId, clientId: ClientId, boxName: String)
               (implicit ec: ExecutionContext): Future[BoxCreateResult] = {
    repository.createBox(Box(boxId, boxName, BoxCreator(clientId))) flatMap {
      case Some(id) => Future.successful(BoxCreatedResult(id))
      case None => repository.getBoxByNameAndClientId(boxName, clientId)
        .map(_.headOption) map {
        case Some(x) => BoxRetrievedResult(x.boxId)
        case _ =>
          Logger.info(s"Box with name :$boxName already exists for clientId: $clientId but unable to retrieve")
          BoxCreateFailedResult(s"Box with name :$boxName already exists for this clientId but unable to retrieve it")
      }
    }

  }

  def getBoxByNameAndClientId(boxName: String, clientId: ClientId)
                             (implicit ec: ExecutionContext): Future[List[Box]] = {
    repository.getBoxByNameAndClientId(boxName, clientId)
  }

  def updateSubscribers(boxId: BoxId, request: UpdateSubscribersRequest)
                       (implicit ec: ExecutionContext): Future[Option[Box]] = {
    val subscribers = request.subscribers.map(subscriber => {
      subscriber.subscriberType match {
        case API_PULL_SUBSCRIBER => subscriber.subscriberId match {
          case Some(id) => PullSubscriber(callBackUrl = subscriber.callBackUrl, subscriberId = SubscriberId.fromString(id))
          case None => PullSubscriber(callBackUrl = subscriber.callBackUrl)
        }
        case API_PUSH_SUBSCRIBER => subscriber.subscriberId match {
          case Some(id) => PushSubscriber(callBackUrl = subscriber.callBackUrl, subscriberId =  SubscriberId.fromString(id))
          case None => PushSubscriber(callBackUrl = subscriber.callBackUrl)
        }
      }
    })
    repository.updateSubscribers(boxId, subscribers.map(new SubscriberContainer(_)))
  }

}

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
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType._
import uk.gov.hmrc.pushpullnotificationsapi.models.{TopicServiceCreateFailedResult, _}
import uk.gov.hmrc.pushpullnotificationsapi.repository.TopicsRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TopicsService @Inject()(repository: TopicsRepository) {

  def createTopic(topicId: TopicId, clientId: ClientId, topicName: String)
                 (implicit ec: ExecutionContext): Future[Either[TopicServiceCreateFailedResult, TopicServiceSuccessResult]] = {
    repository.createTopic(Topic(topicId, topicName, TopicCreator(clientId))) flatMap {
      case Some(id) => Future.successful(Right(TopicServiceCreateSuccessResult(id)))
      case None => repository.getTopicByNameAndClientId(topicName, clientId)
        .map(_.headOption) map {
        case Some(x) => Right(TopicServiceCreateRetrievedSuccessResult(x.topicId))
        case _ => Left(TopicServiceCreateFailedResult(s"Topic with name :$topicName already exists for cleintId: $clientId but unable to retrieve"))
      }
    }

  }

  def getTopicByNameAndClientId(topicName: String, clientId: ClientId)
                               (implicit ec: ExecutionContext): Future[List[Topic]] = {
    repository.getTopicByNameAndClientId(topicName, clientId)
  }

  def updateSubscribers(topicId: TopicId, request: UpdateSubscribersRequest)
                       (implicit ec: ExecutionContext): Future[Option[Topic]] = {
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
    repository.updateSubscribers(topicId, subscribers.map(new SubscriberContainer(_)))
  }

}

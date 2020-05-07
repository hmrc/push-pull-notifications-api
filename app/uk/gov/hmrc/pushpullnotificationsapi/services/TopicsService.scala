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
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.TopicsRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TopicsService @Inject()(repository: TopicsRepository) {

  def createTopic(topicId: String, clientId: String, topicName: String)
                 (implicit ec: ExecutionContext): Future[Unit] = {
    repository.createTopic(Topic(topicId, topicName, TopicCreator(clientId)))
  }

  def getTopicByNameAndClientId(topicName: String, clientId:String)
                               (implicit ec: ExecutionContext): Future[List[Topic]] ={
    repository.getTopicByNameAndClientId(topicName, clientId)
  }

  def updateSubscribers(topicId: String, request: UpdateSubscribersRequest)
                                (implicit ec: ExecutionContext): Future[Option[Topic]] = {
    val subscribers = request.subscribers.map(subscriber => {
      subscriber.subscriberType match {
        case API_PUSH_SUBSCRIBER => subscriber.subscriberId match {
          case Some(id) => PushSubscriber(callBackUrl = subscriber.callBackUrl, subscriberId = id)
          case None => PushSubscriber(callBackUrl = subscriber.callBackUrl)
        }
      }
    })
    repository.updateSubscribers(topicId, subscribers.map(new SubscriberContainer(_)))
  }

}

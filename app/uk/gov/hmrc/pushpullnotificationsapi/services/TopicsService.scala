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

import java.util.UUID

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc.Result
import play.api.mvc.Results.{Ok, Created}
import uk.gov.hmrc.pushpullnotificationsapi.models.{Topic, TopicCreator}
import uk.gov.hmrc.pushpullnotificationsapi.repository.TopicsRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TopicsService @Inject()(repository: TopicsRepository) {
  def createTopic(clientId: String, topicName: String)
                 (implicit ec: ExecutionContext): Future[Result] = {
    val topicId = UUID.randomUUID().toString
    repository.createTopic(Topic(topicId, topicName, TopicCreator(clientId))).map {
      case true =>
        Logger.info(s"Topic Created: $topicId for clientId: $clientId")
        Created
      case false => Ok
    }
  }

}

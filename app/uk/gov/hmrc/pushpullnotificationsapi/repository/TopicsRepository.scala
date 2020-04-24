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

package uk.gov.hmrc.pushpullnotificationsapi.repository

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models.Topic

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class TopicsRepository @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Topic, BSONObjectID](
    "push-pull-notification-topics",
    mongoComponent.mongoConnector.db,
    Topic.formats,
    ReactiveMongoFormats.objectIdFormats) {

  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats

  override def indexes = Seq(
    Index(
      key = Seq(
        "topicName" -> IndexType.Ascending,
        "topicCreator.clientId" -> IndexType.Ascending
      ),
      name = Some("push_pull_notification_topics_index"),
      unique = true,
      background = true
    )
  )

  def createTopic(topic: Topic)(
    implicit ec: ExecutionContext): Future[Boolean] =
    insert(topic).map(wr => wr.ok) recover {
      case NonFatal(e) =>
        Logger.info(s"Exception occurred creating Topic with name: ${topic.topicName} error: ${e.getMessage}")
        false
    }

}


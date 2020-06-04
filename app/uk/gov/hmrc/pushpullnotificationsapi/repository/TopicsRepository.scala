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

import java.util.UUID

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models.ReactiveMongoFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.util.mongo.IndexHelper.{createAscendingIndex, createSingleFieldAscendingIndex}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TopicsRepository @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Topic, BSONObjectID](
    "topics",
    mongoComponent.mongoConnector.db,
    ReactiveMongoFormatters.topicsFormats,
    ReactiveMongoFormats.objectIdFormats) {

  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats

  override def indexes = Seq(
    createAscendingIndex(Some("topics_index"),
      isUnique = true,
      isBackground = true,
      indexFieldsKey = List("topicName", "topicCreator.clientId.value"): _*),
    createSingleFieldAscendingIndex("topicId", Some("topicid_index"), isUnique = true)
  )


  def findByTopicId(topicId: TopicId)(implicit executionContext: ExecutionContext): Future[List[Topic]] = {
    find("topicId.value" -> topicId.value)
  }

  def createTopic(topic: Topic)(implicit ec: ExecutionContext): Future[Option[TopicId]] =
    collection.insert.one(topic).map(_ => Some(topic.topicId)).recoverWith {
      case e: WriteResult if e.code.contains(MongoErrorCodes.DuplicateKey) =>
        Logger.info("unable to create topic")
      Future.successful(None)
    }

  def getTopicByNameAndClientId(topicName: String, clientId: ClientId)(implicit executionContext: ExecutionContext): Future[List[Topic]] = {
    Logger.info(s"Getting topic by topicName:$topicName & clientId")
    find("topicName" -> topicName, "topicCreator.clientId.value" -> clientId.value)
  }

  def updateSubscribers(topicId: TopicId, subscribers: List[SubscriberContainer[Subscriber]])(implicit ec: ExecutionContext): Future[Option[Topic]] = {

    updateTopic(topicId.value, Json.obj("$set" -> Json.obj("subscribers" -> subscribers.map(_.elem))))
  }

  private def updateTopic(topicId: UUID, updateStatement: JsObject)(implicit ec: ExecutionContext): Future[Option[Topic]] =
    findAndUpdate(Json.obj("topicId.value" -> topicId), updateStatement, fetchNewObject = true) map {
      _.result[Topic]
    }
}


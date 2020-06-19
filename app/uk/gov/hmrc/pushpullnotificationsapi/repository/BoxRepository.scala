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
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models.ReactiveMongoFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.util.mongo.IndexHelper.{createAscendingIndex, createSingleFieldAscendingIndex}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BoxRepository @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Box, BSONObjectID](
    "box",
    mongoComponent.mongoConnector.db,
    ReactiveMongoFormatters.boxFormats,
    ReactiveMongoFormats.objectIdFormats) {

  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats

  override def indexes = Seq(
    createAscendingIndex(Some("box_index"),
      isUnique = true,
      isBackground = true,
      indexFieldsKey = List("boxName", "boxCreator.clientId"): _*),
    createSingleFieldAscendingIndex("boxId", Some("boxid_index"), isUnique = true)
  )


  def findByBoxId(boxId: BoxId)(implicit executionContext: ExecutionContext): Future[List[Box]] = {
    find("boxId" -> boxId.value)
  }

  def createBox(box: Box)(implicit ec: ExecutionContext): Future[Option[BoxId]] =
    collection.insert.one(box).map(_ => Some(box.boxId)).recoverWith {
      case e: WriteResult if e.code.contains(MongoErrorCodes.DuplicateKey) =>
        Logger.info("unable to create box")
      Future.successful(None)
    }

  def getBoxByNameAndClientId(boxName: String, clientId: ClientId)(implicit executionContext: ExecutionContext): Future[List[Box]] = {
    Logger.info(s"Getting box by boxName:$boxName & clientId")
    find("boxName" -> boxName, "boxCreator.clientId" -> clientId.value)
  }

  def updateSubscribers(boxId: BoxId, subscribers: List[SubscriberContainer[Subscriber]])(implicit ec: ExecutionContext): Future[Option[Box]] = {

    updateBox(boxId, Json.obj("$set" -> Json.obj("subscribers" -> subscribers.map(_.elem))))
  }

  private def updateBox(boxId: BoxId, updateStatement: JsObject)(implicit ec: ExecutionContext): Future[Option[Box]] =
    findAndUpdate(Json.obj("boxId" -> boxId.value), updateStatement, fetchNewObject = true) map {
      _.result[Box]
    }
}

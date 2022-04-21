/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.ReactiveMongoFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.util.mongo.IndexHelper.{createAscendingIndex, createSingleFieldAscendingIndex}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class BoxRepository @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Box, BSONObjectID](
    "box",
    mongoComponent.mongoConnector.db,
    boxFormats,
    ReactiveMongoFormats.objectIdFormats) {

  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats

  override def indexes = Seq(
    createAscendingIndex(Some("box_index"),
      isUnique = true,
      isBackground = true,
      indexFieldsKey = List("boxName", "boxCreator.clientId"): _*),
    createSingleFieldAscendingIndex("boxId", Some("boxid_index"), isUnique = true)
  )


  def findByBoxId(boxId: BoxId)(implicit executionContext: ExecutionContext): Future[Option[Box]] = {
    find("boxId" -> boxId.value).map(_.headOption)
  }

  def getAllBoxes()(implicit ec: ExecutionContext) : Future[List[Box]] = {
    findAll()
  }

  def createBox(box: Box)(implicit ec: ExecutionContext): Future[CreateBoxResult] =
    collection.insert.one(box).map(_ => BoxCreatedResult(box)) recoverWith {
      case NonFatal(e) => Future.successful(BoxCreateFailedResult(e.getMessage))
    }

  def getBoxByNameAndClientId(boxName: String, clientId: ClientId)(implicit executionContext: ExecutionContext): Future[Option[Box]] = {
    logger.info(s"Getting box by boxName:$boxName & clientId")
    find("boxName" -> boxName, "boxCreator.clientId" -> clientId.value).map(_.headOption)
  }

  def getBoxesByClientId(clientId: ClientId)(implicit executionContext: ExecutionContext): Future[List[Box]] = {
    logger.info(s"Getting boxes by clientId: $clientId")
    find("boxCreator.clientId" -> clientId.value)
  }

  def updateSubscriber(boxId: BoxId, subscriber: SubscriberContainer[Subscriber])(implicit ec: ExecutionContext): Future[Option[Box]] = {
    updateBox(boxId, Json.obj("$set" -> Json.obj("subscriber" -> subscriber.elem)))
  }

  def updateApplicationId(boxId: BoxId, applicationId: ApplicationId)(implicit ec: ExecutionContext): Future[Box] = {
    updateBox(boxId, Json.obj("$set" -> Json.obj("applicationId" -> applicationId)))
      .flatMap {
        case Some(box) => Future.successful(box)
        case None => Future.failed(new RuntimeException(s"Unable to update box $boxId with applicationId"))
      }
  }

  private def updateBox(boxId: BoxId, updateStatement: JsObject)(implicit ec: ExecutionContext): Future[Option[Box]] =
    findAndUpdate(Json.obj("boxId" -> boxId.value), updateStatement, fetchNewObject = true) map {
      _.result[Box]
    }
}





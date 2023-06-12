/*
 * Copyright 2023 HM Revenue & Customs
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
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import org.mongodb.scala.model.Filters.{equal, _}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model._

import play.api.Logger
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.API_PUSH_SUBSCRIBER
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.BoxFormat
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.PlayHmrcMongoFormatters.{boxIdFormatter, formatSubscriber}

@Singleton
class BoxRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[Box](
      collectionName = "box",
      mongoComponent = mongo,
      domainFormat = BoxFormat.boxFormats,
      indexes = Seq(
        IndexModel(
          ascending(List("boxName", "boxCreator.clientId"): _*),
          IndexOptions()
            .name("box_index")
            .background(true)
            .unique(true)
        ),
        IndexModel(
          ascending("boxId"),
          IndexOptions()
            .name("boxidu_index")
            .unique(true)
        )
      ),
      replaceIndexes = true
    )
    with MongoJavatimeFormats.Implicits {

  private val logger = Logger(this.getClass)

  def findByBoxId(boxId: BoxId): Future[Option[Box]] = {
    collection.find(equal("boxId", Codecs.toBson(boxId))).headOption()
  }

  def getAllBoxes(): Future[List[Box]] = {
    collection.find().toFuture().map(_.toList)
  }

  def createBox(box: Box): Future[CreateBoxResult] =
    collection.insertOne(box).map(_ => BoxCreatedResult(box)).head() recoverWith {
      case NonFatal(e) => Future.successful(BoxCreateFailedResult(e.getMessage))
    }

  def deleteBox(boxId: BoxId): Future[DeleteBoxResult] =
    collection.deleteOne(equal("boxId", Codecs.toBson(boxId))).map(_ => BoxDeleteSuccessfulResult()).head() recoverWith {
      case NonFatal(e) => Future.successful(BoxDeleteFailedResult(e.getMessage))
    }

  def getBoxByNameAndClientId(boxName: String, clientId: ClientId): Future[Option[Box]] = {
    logger.info(s"Getting box by boxName:$boxName & clientId: ${clientId.value}")
    collection.find(Filters.and(equal("boxName", Codecs.toBson(boxName)), equal("boxCreator.clientId", Codecs.toBson(clientId)))).headOption()
  }

  def getBoxesByClientId(clientId: ClientId): Future[List[Box]] = {
    logger.info(s"Getting boxes by clientId: ${clientId.value}")
    collection.find(equal("boxCreator.clientId", Codecs.toBson(clientId))).toFuture().map(_.toList)
  }

  def updateSubscriber(boxId: BoxId, subscriber: SubscriberContainer[Subscriber]): Future[Option[Box]] = {
    collection.findOneAndUpdate(
      equal("boxId", Codecs.toBson(boxId.value)),
      update = set("subscriber", Codecs.toBson(subscriber.elem)),
      options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
    ).headOption()
  }

  def updateApplicationId(boxId: BoxId, applicationId: ApplicationId): Future[Box] = {
    collection.findOneAndUpdate(
      equal("boxId", Codecs.toBson(boxId.value)),
      update = set("applicationId", Codecs.toBson(applicationId)),
      options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
    ).headOption()
      .flatMap {
        case Some(box) => Future.successful(box)
        case None      => Future.failed(new RuntimeException(s"Unable to update box $boxId with applicationId"))
      }
  }

  def fetchPushSubscriberBoxes(): Future[List[Box]] = {
    collection.find(
      and(
        equal("subscriber.subscriptionType", Codecs.toBson(API_PUSH_SUBSCRIBER)),
        Filters.exists("subscriber.callBackUrl"),
        Filters.ne("subscriber.callBackUrl", "")
      )
    ).toFuture().map(_.toList)
  }
}

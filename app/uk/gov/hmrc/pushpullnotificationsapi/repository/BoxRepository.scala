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

import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromRegistries}

import javax.inject.{Inject, Singleton}
import org.mongodb.scala.{MongoClient, MongoCollection}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, IndexModel, IndexOptions, ReturnDocument, Updates}
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.PlayHmrcMongoFormatters
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.PlayHmrcMongoFormatters._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class BoxRepository @Inject()(mongo: MongoComponent)
                             (implicit ec: ExecutionContext)
  extends PlayMongoRepository[Box](
    collectionName ="box",
    mongoComponent = mongo,
    domainFormat = boxFormats,
    indexes = Seq(
      IndexModel(ascending(List("boxName", "boxCreator.clientId"): _*),
        IndexOptions()
          .name("box_index")
          .background(true)
          .unique(true)),
      IndexModel(ascending("boxId"),
      IndexOptions()
        .name("boxid_index")
        .unique(true)))
    ) {

  private val logger = Logger(this.getClass)

  override lazy val collection: MongoCollection[Box] =
    CollectionFactory
      .collection(mongo.database, collectionName, domainFormat)
      .withCodecRegistry(
        fromRegistries(
          fromCodecs(
            Codecs.playFormatCodec(domainFormat),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.dateFormat),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.clientIdFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.formatBoxCreator),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.boxIdFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.boxFormats),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.applicationIdFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.pushSubscriberFormats),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.pullSubscriberFormats),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.formatSubscriber),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.subscriptionTypePushFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.subscriptionTypePullFormatter)
          ),
          MongoClient.DEFAULT_CODEC_REGISTRY
        )
      )


  def findByBoxId(boxId: BoxId)(implicit executionContext: ExecutionContext): Future[Option[Box]] = {
    logger.info(s"findByBoxId here ${boxId.raw}")
    collection.find(equal("boxId", Codecs.toBson(boxId))).headOption()
  }

  def createBox(box: Box)(implicit ec: ExecutionContext): Future[CreateBoxResult] =
    collection.insertOne(box).map(_ => BoxCreatedResult(box)).head() recoverWith {
      case NonFatal(e) => Future.successful(BoxCreateFailedResult(e.getMessage))
    }

  def getBoxByNameAndClientId(boxName: String, clientId: ClientId)(implicit executionContext: ExecutionContext): Future[Option[Box]] = {
    logger.info(s"Getting box by boxName:$boxName & clientId: ${clientId.value}")
    collection.find(Filters.and(equal("boxName", Codecs.toBson(boxName)), equal("boxCreator.clientId", Codecs.toBson(clientId.value)))).headOption()
  }

  def getBoxesByClientId(clientId: ClientId): Future[List[Box]] = {
    logger.info(s"Getting boxes by clientId: $clientId")
    collection.find(equal("boxCreator.clientId", Codecs.toBson(clientId.value))).toFuture().map(_.toList)
  }

  def updateSubscriber(boxId: BoxId, subscriber: SubscriberContainer[Subscriber])(implicit ec: ExecutionContext): Future[Option[Box]] = {
    collection.findOneAndUpdate(equal("boxId", Codecs.toBson(boxId.value)),
      update = set("subscriber", Codecs.toBson(subscriber.elem)),
      options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
    ).map(_.asInstanceOf[Box]).headOption()
  }

  def updateApplicationId(boxId: BoxId, applicationId: ApplicationId)(implicit ec: ExecutionContext): Future[Box] = {
    collection.findOneAndUpdate(equal("boxId", Codecs.toBson(boxId.value)),
      update = set("applicationId", Codecs.toBson(applicationId)),
      options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).map(_.asInstanceOf[Box]).headOption()
      .flatMap {
        case Some(box) => Future.successful(box)
        case None => Future.failed(new RuntimeException(s"Unable to update box $boxId with applicationId"))
      }
  }
}




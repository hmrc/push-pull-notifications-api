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

import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromRegistries}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, IndexModel, IndexOptions, ReturnDocument}
import org.mongodb.scala.{MongoClient, MongoCollection}
import org.mongodb.scala.model.Aggregates.{`match`, lookup, project, unwind}
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model._

import play.api.Logger
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.BoxFormat._
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.PlayHmrcMongoFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.{BoxFormat, PlayHmrcMongoFormatters}
import akka.stream.scaladsl.Source
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.RetryableNotification
import akka.NotUsed
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.API_PUSH_SUBSCRIBER
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.PENDING
import java.time.Instant
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbRetryableNotification.toRetryableNotification
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbRetryableNotification
import uk.gov.hmrc.crypto.CompositeSymmetricCrypto

@Singleton
class BoxRepository @Inject() (mongo: MongoComponent, crypto: CompositeSymmetricCrypto)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[Box](
      collectionName = "box",
      mongoComponent = mongo,
      domainFormat = boxFormats,
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
            .name("boxid_index")
            .unique(true)
        )
      )
    ) {

  private val logger = Logger(this.getClass)

  override lazy val collection: MongoCollection[Box] =
    CollectionFactory
      .collection(mongo.database, collectionName, domainFormat)
      .withCodecRegistry(
        fromRegistries(
          fromCodecs(
            Codecs.playFormatCodec(domainFormat),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.instantFormat),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.clientIdFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.formatBoxCreator),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.boxIdFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.dbRetryableNotificationFormatter),
            Codecs.playFormatCodec(BoxFormat.boxFormats),
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

  def findByBoxId(boxId: BoxId): Future[Option[Box]] = {
    collection.find(equal("boxId", Codecs.toBson(boxId))).headOption()
  }

  def getAllBoxes()(implicit ec: ExecutionContext): Future[List[Box]] = {
    collection.find().toFuture().map(_.toList)
  }

  def createBox(box: Box)(implicit ec: ExecutionContext): Future[CreateBoxResult] =
    collection.insertOne(box).map(_ => BoxCreatedResult(box)).head() recoverWith {
      case NonFatal(e) => Future.successful(BoxCreateFailedResult(e.getMessage))
    }

  def deleteBox(boxId: BoxId): Future[DeleteBoxResult] =
    collection.deleteOne(equal("boxId", Codecs.toBson(boxId))).map(_ => BoxDeleteSuccessfulResult()).head() recoverWith {
      case NonFatal(e) => Future.successful(BoxDeleteFailedResult(e.getMessage))
    }

  def getBoxByNameAndClientId(boxName: String, clientId: ClientId): Future[Option[Box]] = {
    logger.info(s"Getting box by boxName:$boxName & clientId: ${clientId.value}")
    collection.find(Filters.and(equal("boxName", Codecs.toBson(boxName)), equal("boxCreator.clientId", Codecs.toBson(clientId.value)))).headOption()
  }

  def getBoxesByClientId(clientId: ClientId): Future[List[Box]] = {
    logger.info(s"Getting boxes by clientId: $clientId")
    collection.find(equal("boxCreator.clientId", Codecs.toBson(clientId.value))).toFuture().map(_.toList)
  }

  def updateSubscriber(boxId: BoxId, subscriber: SubscriberContainer[Subscriber]): Future[Option[Box]] = {
    collection.findOneAndUpdate(
      equal("boxId", Codecs.toBson(boxId.value)),
      update = set("subscriber", Codecs.toBson(subscriber.elem)),
      options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
    ).map(_.asInstanceOf[Box]).headOption()
  }

  def updateApplicationId(boxId: BoxId, applicationId: ApplicationId): Future[Box] = {
    collection.findOneAndUpdate(
      equal("boxId", Codecs.toBson(boxId.value)),
      update = set("applicationId", Codecs.toBson(applicationId)),
      options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
    ).map(_.asInstanceOf[Box]).headOption()
      .flatMap {
        case Some(box) => Future.successful(box)
        case None      => Future.failed(new RuntimeException(s"Unable to update box $boxId with applicationId"))
      }
  }

  def fetchRetryableNotifications: Source[RetryableNotification, NotUsed] = {
    val pipeline = List(
      `match`(
        and(
          equal("subscriber.subscriptionType", Codecs.toBson(API_PUSH_SUBSCRIBER)),
          Filters.exists("subscriber.callBackUrl"),
          Filters.ne("subscriber.callBackUrl", "")
        )
      ),
      `lookup`("notifications", "boxId", "boxId", "notification"),
      `match`(
        and(
          equal("notification.status", Codecs.toBson(PENDING)), 
          or(
            Filters.exists("notification.retryAfterDateTime", false),
            lte("notification.retryAfterDateTime", Codecs.toBson(Instant.now))
          )
        )
      ),
      unwind("$notification"),
      project(Document(
        """{ "notification": "$notification",
          | "box": { "boxId": "$boxId", "boxName": "$boxName", "subscriber": "$subscriber", "applicationId": "$applicationId", "boxCreator": "$boxCreator" }
          | }""".stripMargin
      ))
    )
    
    Source.fromPublisher(
      collection.aggregate[DbRetryableNotification](pipeline)
        .map(toRetryableNotification(_, crypto))
    )
  }
}

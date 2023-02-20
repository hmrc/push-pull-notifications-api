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

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import akka.NotUsed
import akka.stream.scaladsl.Source
import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromRegistries}
import org.mongodb.scala.model.Filters.{and, equal, lte, or}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model._
import org.mongodb.scala.{MongoClient, MongoCollection, MongoWriteException}

import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models.ConfirmationId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.PENDING
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.{ConfirmationRequest, PlayHmrcMongoFormatters}

@Singleton
class ConfirmationRepository @Inject() (appConfig: AppConfig, mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[ConfirmationRequest](
      collectionName = "confirmations",
      mongoComponent = mongoComponent,
      domainFormat = PlayHmrcMongoFormatters.confirmationRequestFormatter,
      indexes = Seq(
        IndexModel(
          ascending("confirmationId"),
          IndexOptions()
            .name("confirmations_idx")
            .background(true)
            .unique(true)
        ),
        IndexModel(
          ascending("notificationId"),
          IndexOptions()
            .name("notifications_idx")
            .background(true)
            .unique(true)
        ),
        IndexModel(
          ascending(Seq("createdDateTime"): _*),
          IndexOptions()
            .name("create_datetime_ttl_idx")
            .expireAfter(appConfig.notificationTTLinSeconds, TimeUnit.SECONDS)
            .background(true)
            .unique(false)
        )
      ),
      replaceIndexes = true
    )
    with MongoJavatimeFormats.Implicits {

  override lazy val collection: MongoCollection[ConfirmationRequest] =
    CollectionFactory
      .collection(mongoComponent.database, collectionName, domainFormat)
      .withCodecRegistry(
        fromRegistries(
          fromCodecs(
            Codecs.playFormatCodec(domainFormat),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.instantFormat),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.notificationIdFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.confirmationRequestFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.confirmationIdFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.notificationPendingStatusFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.notificationFailedStatusFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.notificationAckStatusFormatter)
          ),
          MongoClient.DEFAULT_CODEC_REGISTRY
        )
      )

  def saveConfirmationRequest(confirmation: ConfirmationRequest)(implicit ec: ExecutionContext): Future[Option[ConfirmationId]] = {
    collection.insertOne(confirmation)
      .toFuture()
      .map(_ => Some(confirmation.confirmationId)).recoverWith {
        case e: MongoWriteException if e.getCode == MongoErrorCodes.DuplicateKey =>
          Future.successful(None)
      }
  }

  def updateConfirmationNeed(notificationId: NotificationId): Future[Option[ConfirmationRequest]] = {
    collection.findOneAndUpdate(
      filter = equal("notificationId", Codecs.toBson(notificationId.value)),
      update = set("pushedDateTime", Instant.now),
      options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
    ).headOption()
  }

  def updateStatus(notificationId: NotificationId, status: NotificationStatus): Future[Option[ConfirmationRequest]] = {
    collection.findOneAndUpdate(
      filter = equal("notificationId", Codecs.toBson(notificationId.value)),
      update = set("status", Codecs.toBson(status)),
      options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
    ).headOption()
  }

  def updateRetryAfterDateTime(notificationId: NotificationId, retryAfterDateTime: Instant): Future[Option[ConfirmationRequest]] = {
    collection.findOneAndUpdate(
      equal("notificationId", Codecs.toBson(notificationId.value)),
      update = set("retryAfterDateTime", retryAfterDateTime),
      options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
    ).headOption()
  }

  def fetchRetryableConfirmations: Source[ConfirmationRequest, NotUsed] = {
    Source.fromPublisher(
      collection.find(and(equal("status", Codecs.toBson(PENDING)), or(Filters.exists("retryAfterDateTime", false), lte("retryAfterDateTime", Instant.now)))).toObservable()
    )
  }

}

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

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromRegistries}
import org.bson.conversions.Bson
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.joda.time.DateTimeZone.UTC
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.{MongoClient, MongoCollection, MongoWriteException, ReadPreference, SingleObservable, bson}
import org.mongodb.scala.model.Filters.{and, equal, gte, in, lte, or}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.Aggregates.{`lookup`, `match`, project}
import org.mongodb.scala.model.{Aggregates, Filters, FindOneAndUpdateOptions, IndexModel, IndexOptions, Projections, ReturnDocument, Sorts}
import play.api.Logger
import play.api.libs.json.{Format, JsArray, JsNumber, JsString, Json, OFormat}
import uk.gov.hmrc.crypto.CompositeSymmetricCrypto
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.API_PUSH_SUBSCRIBER
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.{ACKNOWLEDGED, PENDING}
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationId, NotificationStatus, RetryableNotification}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbNotification.{fromNotification, toNotification}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbRetryableNotification.toRetryableNotification
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.PlayHmrcMongoFormatters.{boxIdFormatter, dbNotificationFormatter}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.{DbNotification, DbRetryableNotification, PlayHmrcMongoFormatters}

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class NotificationsRepository @Inject()(appConfig: AppConfig, mongoComponent: MongoComponent, crypto: CompositeSymmetricCrypto)
                                       (implicit ec: ExecutionContext, mat: Materializer)
  extends PlayMongoRepository[DbNotification](
    collectionName = "notifications",
    mongoComponent = mongoComponent,
    domainFormat = dbNotificationFormatter,
    indexes = Seq(
      IndexModel(ascending(List("notificationId", "boxId", "status"): _*),
        IndexOptions()
          .name("notifications_idx")
          .background(true)
          .unique(true)),
      IndexModel(ascending(List("boxId, createdDateTime"): _*),
        IndexOptions()
          .name("notifications_created_datetime_idx")
          .background(true)
          .unique(false)
      ),
      IndexModel(ascending(Seq("createdDateTime"): _*),
        IndexOptions()
          .name("create_datetime_ttl_idx")
          .expireAfter(appConfig.notificationTTLinSeconds, TimeUnit.SECONDS)
          .background(true)
          .unique(false)
      )
    ),
    replaceIndexes = true
  ) {

  private val logger = Logger(this.getClass)
  private lazy val create_datetime_ttlIndexName = "create_datetime_ttl_idx"
  private lazy val oldIndexes: List[String] = List(create_datetime_ttlIndexName)

  lazy val numberOfNotificationsToReturn: Int = appConfig.numberOfNotificationsToRetrievePerRequest
  implicit val dateFormation: Format[DateTime] = MongoJodaFormats.dateTimeFormat
  override lazy val collection: MongoCollection[DbNotification] =
    CollectionFactory
      .collection(mongoComponent.database, collectionName, domainFormat)
      .withCodecRegistry(
        fromRegistries(
          fromCodecs(
            Codecs.playFormatCodec(domainFormat),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.dateFormat),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.notificationIdFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.formatBoxCreator),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.boxIdFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.clientIdFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.boxFormats),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.dbRetryableNotificationFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.retryableNotificationFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.dbClientSecretFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.dbClientFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.dbNotificationFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.notificationPendingStatusFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.notificationFailedStatusFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.notificationAckStatusFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.formatSubscriber),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.applicationIdFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.pushSubscriberFormats),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.pullSubscriberFormats),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.subscriptionTypePushFormatter),
            Codecs.playFormatCodec(PlayHmrcMongoFormatters.subscriptionTypePullFormatter)
          ),
          MongoClient.DEFAULT_CODEC_REGISTRY
        )
      )

  override def ensureIndexes: Future[Seq[String]] = {
    ensureLocalIndexes
    super.ensureIndexes
  }

  private def ensureLocalIndexes() = {
    Future.sequence(
      List(
        Future.successful(oldIndexes.map(indexToDrop => {
          collection.dropIndex(indexToDrop)
            .toFuture
            .map(_ => logger.info(s"dropping index $indexToDrop succeeded"))
            .recover {
              case NonFatal(e) => logger.info(s"dropping index $indexToDrop failed ${e.getMessage}")
              case _ => logger.info(s"dropping index $indexToDrop failed")
            }
        })),
        collection.createIndexes(models = indexes)
          .toFuture().map(results => results.map(i => logger.info(s"created Index $i")))
      )
    )
  }

  def getByBoxIdAndFilters(boxId: BoxId,
                           status: Option[NotificationStatus] = None,
                           fromDateTime: Option[DateTime] = None,
                           toDateTime: Option[DateTime] = None,
                           numberOfNotificationsToReturn: Int = numberOfNotificationsToReturn)
                          (implicit ec: ExecutionContext): Future[List[Notification]] = {

    val query: Bson =
      Filters.and(boxIdQuery(boxId),
          statusQuery(status),
          dateRange("createdDateTime", fromDateTime, toDateTime))


    collection
      .withReadPreference(ReadPreference.primaryPreferred)
      .find(query)
      .sort(Sorts.ascending("createdDateTime"))
      .limit(numberOfNotificationsToReturn)
      .map(toNotification(_, crypto))
      .toFuture().map(_.toList)
  }

  private def dateRange(fieldName: String, start: Option[DateTime], end: Option[DateTime]): Bson = {
    if (start.isDefined || end.isDefined) {
      val startCompare = if (start.isDefined) gte(fieldName, Codecs.toBson(start.get)) else Filters.empty()
      val endCompare = if (end.isDefined) lte(fieldName, Codecs.toBson(end.get)) else Filters.empty()
      Filters.and(startCompare, endCompare)
    }
    else Filters.empty()
  }

  private def boxIdQuery(boxId: BoxId): Bson = {
    equal("boxId", Codecs.toBson(boxId.value))
  }

  private def notificationIdsQuery(notificationIds: List[String]): Bson = {
    in("notificationId", notificationIds:_ *)
  }

  private def statusQuery(maybeStatus: Option[NotificationStatus]): Bson = {
    maybeStatus.fold(Filters.empty())(status => equal("status", Codecs.toBson(status)))
  }

  def getAllByBoxId(boxId: BoxId)
                   (implicit ec: ExecutionContext): Future[List[Notification]] = getByBoxIdAndFilters(boxId, numberOfNotificationsToReturn = Int.MaxValue)

  def saveNotification(notification: Notification)(implicit ec: ExecutionContext): Future[Option[NotificationId]] = {
    collection.insertOne(fromNotification(notification, crypto)).toFuture().map(_ => Some(notification.notificationId)).recoverWith {
      case e: MongoWriteException if e.getCode == MongoErrorCodes.DuplicateKey =>
        Future.successful(None)
    }
  }

  def acknowledgeNotifications(boxId: BoxId, notificationIds: List[String])(implicit ec: ExecutionContext): Future[Boolean] = {
    val query = and(boxIdQuery(boxId),
        notificationIdsQuery(notificationIds))

    collection
      .updateMany(query,
        set("status", Codecs.toBson(ACKNOWLEDGED))
      ).toFuture().map(_.wasAcknowledged())
  }

  def updateStatus(notificationId: NotificationId, newStatus: NotificationStatus): Future[Notification] = {
    collection.findOneAndUpdate(equal("notificationId", Codecs.toBson(notificationId.value)),
      update = set("status", Codecs.toBson(newStatus)),
      options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
    ).map(toNotification(_, crypto)).head()
  }

  def updateRetryAfterDateTime(notificationId: NotificationId, newRetryAfterDateTime: DateTime): Future[Notification] = {
    collection.findOneAndUpdate(equal("notificationId", Codecs.toBson(notificationId.value)),
      update = set("retryAfterDateTime", Codecs.toBson(newRetryAfterDateTime)),
      options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
    ).map(toNotification(_, crypto)).head()
  }

  def fetchRetryableNotifications: Source[RetryableNotification, NotUsed] = {
    val pipeline = List(
      `match`(and(equal("status", Codecs.toBson(PENDING)),
        or(Filters.exists("retryAfterDateTime", false), lte("retryAfterDateTime", Codecs.toBson(now(UTC)))))),
      `lookup`("box", "boxId", "boxId", "boxes"),
      `match`(and(equal("boxes.subscriber.subscriptionType", Codecs.toBson(API_PUSH_SUBSCRIBER)),
        Filters.exists("boxes.subscriber.callBackUrl"),
        Filters.ne("boxes.subscriber.callBackUrl", ""))),
      project(Document(
          """{ "notification": {"notificationId": "$notificationId", "boxId": "$boxId", "messageContentType": "$messageContentType",
            | "message" : "$message", "encryptedMessage" : "$encryptedMessage", "status" : "$status", "createdDateTime" : "$createdDateTime",
            | "retryAfterDateTime" : "$retryAfterDateTime"},
            | "box": {"$arrayElemAt": ["$boxes", 0]}
            | }""".stripMargin))

    )

    Source.fromPublisher(
      collection.aggregate[DbRetryableNotification](pipeline)
        .map(toRetryableNotification(_, crypto))
    )

  }
}

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
import com.mongodb.client.model.Aggregates.project
import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromRegistries}
import org.bson.conversions.Bson
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.joda.time.DateTimeZone.UTC
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.{MongoClient, MongoCollection, MongoWriteException, ReadPreference, bson}
import org.mongodb.scala.model.Filters.{and, equal, gte, in, lte, or}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{Aggregates, Filters, FindOneAndUpdateOptions, IndexModel, IndexOptions, Projections, ReturnDocument, Updates}
import play.api.Logger
import play.api.libs.json.Format
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
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.ReactiveMongoFormatters.dbNotificationFormatter
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.{DbNotification, DbRetryableNotification}

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

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
  private lazy val notifications_index_name = "notifications_idx"
  private lazy val created_datetime_index_name = "notifications_created_datetime_idx"
  private lazy val OptExpireAfterSeconds = "expireAfterSeconds"

  lazy val numberOfNotificationsToReturn: Int = appConfig.numberOfNotificationsToRetrievePerRequest
  implicit val dateFormation: Format[DateTime] = MongoJodaFormats.dateTimeFormat

  override lazy val collection: MongoCollection[DbNotification] =
    CollectionFactory
      .collection(mongoComponent.database, collectionName, domainFormat)
      .withCodecRegistry(
        fromRegistries(
          fromCodecs(
            Codecs.playFormatCodec(dateFormation)
          ),
          MongoClient.DEFAULT_CODEC_REGISTRY
        )
      )

  override def ensureIndexes: Future[Seq[String]] = {
    ensureLocalIndexes
    super.ensureIndexes
  }

  private def ensureLocalIndexes()(implicit ec: ExecutionContext) = {
    val indexList = List()
    val currentIndexes = collection.listIndexes()
    indexes.filter(index => {
      val indexFound = currentIndexes.map(curr => curr.contains(index.getOptions.getName)).headOption()
      indexFound.map(indexOpt => indexOpt.getOrElse(false)).value.isDefined
    }).foreach(index => {
      logger.info(s"creating index: $index")
      Future.successful(collection.createIndex(index.getKeys, index.getOptions).head())
    })

/*
    Future.sequence(
      List(
      collection.createIndexes(models = indexes)
        .toFuture().map(results => results.map(i => logger.info(s"created Index $i")))
*/
//      collection.createIndex(index.getKeys, index.getOptions).head()
//
//        indexes.map(index => {
//      logger.info(s"ensuring index before $index")
//      logger.info(s"ensuring index ${index.toString}")
//      collection.createIndex(index.getKeys, index.getOptions).head()
//    })
//  ))
  }

  private def dropTTLIndexIfChanged(implicit ec: ExecutionContext) = {
    val indexes = collection.listIndexes()

    def matchIndexName(indexName: String) = {
      indexName == create_datetime_ttlIndexName
    }

    def compareTTLValueWithConfig(index: bson.BsonValue) = {
      val ttlIndex = index.asDocument().get(OptExpireAfterSeconds)
      ttlIndex.asNumber().intValue() != appConfig.notificationTTLinSeconds
      //      index.options.getAs[BSONLong](OptExpireAfterSeconds).fold(false)(_.as[Long] != appConfig.notificationTTLinSeconds)
    }

    def checkIfTTLChanged(index: (String, bson.BsonValue)): Boolean = {
      matchIndexName(index._1) && compareTTLValueWithConfig(index._2)
    }

    indexes.map(_.exists(index => checkIfTTLChanged(index)))
      .map(hasTTLIndexChanged => if (hasTTLIndexChanged) {
        logger.info(s"Dropping time to live index for entries in ${collection}")
        collection.dropIndex(create_datetime_ttlIndexName)
        ensureLocalIndexes
      })
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
      .withReadPreference(ReadPreference.secondaryPreferred)
      .find(query)
      .sort(equal("createdDateTime", 1))
      .limit(numberOfNotificationsToReturn)
      .map(toNotification(_, crypto))
      .toFuture().map(_.toList)
  }

  private def dateRange(fieldName: String, start: Option[DateTime], end: Option[DateTime]): Bson = {
    if (start.isDefined || end.isDefined) {
      val startCompare = if (start.isDefined) gte(fieldName, start.get.getMillis) else Filters.empty()
      val endCompare = if (end.isDefined) lte(fieldName, end.get.getMillis) else Filters.empty()
      Filters.and(startCompare, endCompare)
    }
    else Filters.empty()
  }

  private def boxIdQuery(boxId: BoxId): Bson = {
    equal("boxId", boxId.value)
  }

  private def notificationIdsQuery(notificationIds: List[String]): Bson = {
    in("notificationId", notificationIds)
  }

  private def statusQuery(maybeStatus: Option[NotificationStatus]): Bson = {
    if(maybeStatus.isDefined) equal("status", maybeStatus.get) else Filters.empty()
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
        set("status", ACKNOWLEDGED)
      ).toFuture().map(_.wasAcknowledged())
  }

  def updateStatus(notificationId: NotificationId, newStatus: NotificationStatus): Future[Notification] = {
    collection.findOneAndUpdate(equal("notificationId", Codecs.toBson(notificationId.value)),
      update = set("status", newStatus),
      options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).map(toNotification(_, crypto)).head()
  }

  def updateRetryAfterDateTime(notificationId: NotificationId, newRetryAfterDateTime: DateTime): Future[Notification] = {
    collection.findOneAndUpdate(equal("notificationId", Codecs.toBson(notificationId.value)),
      update = set("retryAfterDateTime", newRetryAfterDateTime),
      options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).map(toNotification(_, crypto)).head()
  }

  def fetchRetryableNotifications: Source[RetryableNotification, NotUsed] = {

//    val builder = collection.BatchCommands.AggregationFramework
    val pipeline = List(
//      builder.Match(Json.obj("$and" -> Json.arr(Json.obj("status" -> PENDING),
//        Json.obj("$or" -> Json.arr(Json.obj("retryAfterDateTime" -> Json.obj("$lte" -> now(UTC))),
//          Json.obj("retryAfterDateTime" -> Json.obj("$exists" -> false))))))),

      and(equal("status", PENDING),
        or(Filters.exists("retryAfterDateTime", false), lte("retryAfterDateTime", now(UTC)))),

      Aggregates.lookup("box", "boxId", "boxId", "boxes"),

//      builder.Lookup(from = "box", localField = "boxId", foreignField = "boxId", as = "boxes"),


//      builder.Match(Json.obj("$and" -> Json.arr(Json.obj("boxes.subscriber.subscriptionType" -> API_PUSH_SUBSCRIBER),
//        Json.obj("boxes.subscriber.callBackUrl" -> Json.obj("$exists" -> true, "$ne" -> ""))))),

        and(equal("boxes.subscriber.subscriptionType", API_PUSH_SUBSCRIBER),
          Filters.exists("boxes.subscriber.callBackUrl"),
          Filters.ne("boxes.subscriber.callBackUrl", "")))

//      builder.Project(
//        Json.obj("notification" -> Json.obj("notificationId" -> "$notificationId", "boxId" -> "$boxId", "messageContentType" -> "$messageContentType",
//          "message" -> "$message", "encryptedMessage" -> "$encryptedMessage", "status" -> "$status", "createdDateTime" -> "$createdDateTime",
//          "retryAfterDateTime" -> "$retryAfterDateTime"),
//        "box" -> Json.obj("$arrayElemAt" -> JsArray(Seq(JsString("$boxes"), JsNumber(0))))
//      ))

//    val project = Document(Json.obj("$project" ->
//        Json.obj("notification" ->
//          Json.obj("notificationId" -> "$notificationId", "boxId" -> "$boxId", "messageContentType" -> "$messageContentType",
//                  "message" -> "$message", "encryptedMessage" -> "$encryptedMessage", "status" -> "$status",
//            "createdDateTime" -> "$createdDateTime", "retryAfterDateTime" -> "$retryAfterDateTime")).toString))
    Aggregates.project(
      Projections.fields(
        Projections.include("data"),
        Projections.exclude("_id")
      )
    )

    Source.fromPublisher(
      collection.aggregate[DbRetryableNotification](pipeline)
        //[DbRetryableNotification]()(_ => (pipeline.head, pipeline.tail))
  //      .documentSource()

        .map(toRetryableNotification(_, crypto))
    )

  }
}

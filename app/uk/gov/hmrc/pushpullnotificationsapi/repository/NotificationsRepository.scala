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
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.util.mongo.IndexHelper.createAscendingIndex

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationsRepository @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Notification, BSONObjectID](
    "notifications",
    mongoComponent.mongoConnector.db,
    ReactiveMongoFormatters.notificationsFormats,
    ReactiveMongoFormats.objectIdFormats) with ReactiveMongoFormats {



  override def indexes = Seq(
   createAscendingIndex(
     Some("notifications_index"),
     isUnique = true,
     isBackground = true,
     List("notificationId.value", "topicId.value", "status"): _*
   ),
    createAscendingIndex(
      Some("notifications_created_datetime_index"),
      isUnique = false,
      isBackground = true,
      List("topicId.value, createdDateTime"): _*
    )
  )

  def getByTopicIdAndFilters(topicId: TopicId,
                             status: Option[NotificationStatus] = None,
                             fromDateTime: Option[DateTime] = None,
                             toDateTime: Option[DateTime] = None)
                           (implicit ec: ExecutionContext): Future[List[Notification]] =
  {

    val query: (String, JsValueWrapper) =  f"$$and" -> (
      topicIdQuery(topicId) ++
      statusQuery(status) ++
      Json.arr(dateRange("createdDateTime", fromDateTime, toDateTime)))
    find(query)
  }

  val empty: JsObject = Json.obj()

 private def dateRange(fieldName: String, start: Option[DateTime], end: Option[DateTime]): JsObject = {
    if (start.isDefined || end.isDefined) {
      val startCompare = if (start.isDefined) Json.obj("$gte" -> Json.obj("$date" -> start.get.getMillis)) else empty
      val endCompare = if (end.isDefined) Json.obj("$lte" -> Json.obj("$date" -> end.get.getMillis)) else empty
      Json.obj(fieldName -> (startCompare ++ endCompare))
    }
    else empty
  }

  private def topicIdQuery(topicId: TopicId): JsArray ={
    Json.arr(Json.obj("topicId.value" -> topicId.value))
  }
  private def statusQuery(maybeStatus: Option[NotificationStatus]): JsArray ={
    maybeStatus.fold(Json.arr()) { status => Json.arr(Json.obj("status" -> status)) }
  }

  def getAllByTopicId(topicId: TopicId)
                     (implicit ec: ExecutionContext): Future[List[Notification]] = getByTopicIdAndFilters(topicId)

  def saveNotification(notification: Notification)(implicit ec: ExecutionContext): Future[Option[NotificationId]] =
    insert(notification).map(_ => Some(notification.notificationId)).recoverWith {
      case e: WriteResult if e.code.contains(MongoErrorCodes.DuplicateKey) =>
        Future.successful(None)
    }

}


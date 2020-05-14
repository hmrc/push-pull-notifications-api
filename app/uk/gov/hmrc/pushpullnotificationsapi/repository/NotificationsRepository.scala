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
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models.ReactiveMongoFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.Notification

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationsRepository @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Notification, BSONObjectID](
    "notifications",
    mongoComponent.mongoConnector.db,
    ReactiveMongoFormatters.notificationsFormats,
    ReactiveMongoFormats.objectIdFormats) {

  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats


  //TODO: Think of sensible index (notification id, topicid and createdDateTime)?
  override def indexes = Seq(
    Index(
      key = Seq(
        "notificationId" -> IndexType.Ascending,
        "topicId" -> IndexType.Ascending,
        "status" -> IndexType.Ascending
      ),
      name = Some("notifications_index"),
      unique = true,
      background = true
    )
  )

  def saveNotification(notification: Notification)(implicit ec: ExecutionContext): Future[Unit] =
    collection.insert.one(notification).map(_ => ()).recoverWith {
      case e: WriteResult if e.code.contains(MongoErrorCodes.DuplicateKey) =>
        Future.failed(DuplicateNotificationException(s"${notification.notificationId} ${notification.topicId}"))
    }

}


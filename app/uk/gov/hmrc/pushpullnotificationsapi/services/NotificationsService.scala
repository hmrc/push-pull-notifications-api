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

package uk.gov.hmrc.pushpullnotificationsapi.services

import java.util.UUID

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.pushpullnotificationsapi.models.TopicNotFoundException
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationContentType}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{NotificationsRepository, TopicsRepository}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal


@Singleton
class NotificationsService @Inject()(topicsRepository: TopicsRepository, notificationsRepository: NotificationsRepository) {

  def saveNotification(topicId: String, notificationId: UUID, contentType: NotificationContentType.Value, message: String)(implicit ec: ExecutionContext): Future[Boolean] = {

    topicsRepository.findByTopicId(topicId)
      .flatMap {
        case Nil => Future.failed(TopicNotFoundException(s"$topicId not found"))
        //TODO -> throw exception
        case List(_) => {
          val notification = Notification(notificationId, topicId, contentType, message)
          notificationsRepository.saveNotification(notification).map(_ => true)
        }
      }
  }

  //TODO -> topic Id (on URl) check topic exists...
  //if exists save notification in notification repository.
  // if topic does not exists sent error
  // return any repo errors

}

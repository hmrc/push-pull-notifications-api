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

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, MessageContentType, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{NotificationsRepository, TopicsRepository}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class NotificationsService @Inject()(topicsRepository: TopicsRepository, notificationsRepository: NotificationsRepository) {

  def getNotifications(topicId: TopicId,
                       clientId: ClientId,
                       status: Option[NotificationStatus] = None,
                       fromDateTime: Option[DateTime] = None,
                       toDateTime: Option[DateTime] = None)
                      (implicit ec: ExecutionContext): Future[GetNotificationCreateServiceResult] = {

    topicsRepository.findByTopicId(topicId)
      .flatMap {
        case Nil => Future.successful(GetNotificationsServiceTopicNotFoundResult(s"Topic Id: $topicId not found"))
        case List(x) =>
          if(x.topicCreator.clientId.equals(clientId)) {
            notificationsRepository.getByTopicIdAndFilters(topicId, status, fromDateTime, toDateTime)
              .map(results => GetNotificationsSuccessRetrievedResult(results))
          }else{
            Future.successful(GetNotificationsServiceUnauthorisedResult("clientId does not match topicCreator"))
          }
      }
  }

  def saveNotification(topicId: TopicId, notificationId: NotificationId, contentType: MessageContentType, message: String)
                      (implicit ec: ExecutionContext): Future[NotificationCreateServiceResult] = {
    topicsRepository.findByTopicId(topicId)
      .flatMap {
        case Nil => Future.successful(NotificationCreateFailedTopicNotFoundResult(s"Topic Id: $topicId not found"))
        case List(_) =>
          val notification = Notification(notificationId, topicId, contentType, message)
          notificationsRepository.saveNotification(notification).map {
            case Some(_) => NotificationCreateSuccessResult()
            case None => NotificationCreateFailedDuplicateResult(s"unable to create notification Duplicate found")
          }
      }
  }

}

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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{BoxRepository, NotificationsRepository}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class NotificationsService @Inject()(boxRepository: BoxRepository, notificationsRepository: NotificationsRepository, pushService: NotificationPushService) {

  def getNotifications(boxId: BoxId,
                       clientId: ClientId,
                       status: Option[NotificationStatus] = None,
                       fromDateTime: Option[DateTime] = None,
                       toDateTime: Option[DateTime] = None)
                      (implicit ec: ExecutionContext): Future[GetNotificationCreateServiceResult] = {

    boxRepository.findByBoxId(boxId)
      .flatMap {
        case Nil => Future.successful(GetNotificationsServiceBoxNotFoundResult(s"BoxId: $boxId not found"))
        case List(x) =>
          if(x.boxCreator.clientId.equals(clientId)) {
            notificationsRepository.getByBoxIdAndFilters(boxId, status, fromDateTime, toDateTime)
              .map(results => GetNotificationsSuccessRetrievedResult(results))
          }else{
            Future.successful(GetNotificationsServiceUnauthorisedResult("clientId does not match boxCreator"))
          }
      }
  }

  def saveNotification(boxId: BoxId, notificationId: NotificationId, contentType: MessageContentType, message: String)
                      (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[NotificationCreateServiceResult] = {
    boxRepository.findByBoxId(boxId)
      .flatMap {
        case Nil => Future.successful(NotificationCreateFailedBoxIdNotFoundResult(s"BoxId: $boxId not found"))
        case List(box) =>
          val notification = Notification(notificationId, boxId, contentType, message)
          notificationsRepository.saveNotification(notification).map {
            case Some(_) => if(!box.subscribers.isEmpty) pushService.handlePushNotification(box.subscribers, notification)
              NotificationCreateSuccessResult()
            case None => NotificationCreateFailedDuplicateResult(s"unable to create notification Duplicate found")
          }
      }
  }

}

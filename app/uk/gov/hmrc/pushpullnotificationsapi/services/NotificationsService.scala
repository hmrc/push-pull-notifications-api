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

package uk.gov.hmrc.pushpullnotificationsapi.services

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{BoxRepository, NotificationsRepository}
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationsService @Inject() (boxRepository: BoxRepository, notificationsRepository: NotificationsRepository, pushService: NotificationPushService)
    extends ApplicationLogger {

  def acknowledgeNotifications(
      boxId: BoxId,
      clientId: ClientId,
      request: AcknowledgeNotificationsRequest
    )(implicit ec: ExecutionContext
    ): Future[AcknowledgeNotificationsServiceResult] = {
    boxRepository.findByBoxId(boxId)
      .flatMap {
        case None      => Future.successful(AcknowledgeNotificationsServiceBoxNotFoundResult(s"BoxId: $boxId not found"))
        case Some(box) =>
          if (box.boxCreator.clientId.equals(clientId)) {
            notificationsRepository.acknowledgeNotifications(boxId, request.notificationIds)
              .map(AcknowledgeNotificationsSuccessUpdatedResult)
          } else {
            Future.successful(AcknowledgeNotificationsServiceUnauthorisedResult("clientId does not match boxCreator"))
          }
      }
  }

  def getNotifications(
      boxId: BoxId,
      clientId: ClientId,
      status: Option[NotificationStatus] = None,
      fromDateTime: Option[DateTime] = None,
      toDateTime: Option[DateTime] = None
    )(implicit ec: ExecutionContext
    ): Future[GetNotificationCreateServiceResult] = {

    boxRepository.findByBoxId(boxId)
      .flatMap {
        case None      => Future.successful(GetNotificationsServiceBoxNotFoundResult(s"BoxId: ${boxId.raw} not found"))
        case Some(box) =>
          if (box.boxCreator.clientId.equals(clientId)) {
            notificationsRepository.getByBoxIdAndFilters(boxId, status, fromDateTime, toDateTime)
              .map(results => GetNotificationsSuccessRetrievedResult(results))
          } else {
            Future.successful(GetNotificationsServiceUnauthorisedResult("clientId does not match boxCreator"))
          }
      }
  }

  def saveNotification(
      boxId: BoxId,
      notificationId: NotificationId,
      contentType: MessageContentType,
      message: String
    )(implicit ec: ExecutionContext,
      hc: HeaderCarrier
    ): Future[NotificationCreateServiceResult] = {
    boxRepository.findByBoxId(boxId)
      .flatMap {
        case None      => Future.successful(NotificationCreateFailedBoxIdNotFoundResult(s"BoxId: $boxId not found"))
        case Some(box) =>
          val notification = Notification(notificationId, boxId, contentType, message)
          notificationsRepository.saveNotification(notification).map {
            case Some(_) =>
              pushService.handlePushNotification(box, notification)
              NotificationCreateSuccessResult()
            case None    => NotificationCreateFailedDuplicateResult(s"unable to create notification Duplicate found")
          }
      }
  }

}

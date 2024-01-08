/*
 * Copyright 2024 HM Revenue & Customs
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

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{BoxRepository, NotificationsRepository}
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

@Singleton
class NotificationsService @Inject() (
    boxRepository: BoxRepository,
    notificationsRepository: NotificationsRepository,
    pushService: NotificationPushService,
    confirmationService: ConfirmationService
  )(implicit ec: ExecutionContext)
    extends ApplicationLogger {

  def acknowledgeNotifications(
      boxId: BoxId,
      clientId: ClientId,
      request: AcknowledgeNotificationsRequest
    )(implicit hc: HeaderCarrier
    ): Future[AcknowledgeNotificationsServiceResult] = {
    boxRepository.findByBoxId(boxId)
      .flatMap {
        case None      => Future.successful(AcknowledgeNotificationsServiceBoxNotFoundResult(s"BoxId: $boxId not found"))
        case Some(box) =>
          if (box.boxCreator.clientId == clientId) {
            notificationsRepository.acknowledgeNotifications(boxId, request.notificationIds)
              .map(result => {
                request.notificationIds
                  .foreach(confirmationService.handleConfirmation)
                result
              })
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
      fromDateTime: Option[Instant] = None,
      toDateTime: Option[Instant] = None
    ): Future[Either[GetNotificationsServiceFailedResult, List[Notification]]] = {

    boxRepository.findByBoxId(boxId)
      .flatMap {
        case None      => Future.successful(Left(GetNotificationsServiceBoxNotFoundResult(s"BoxId: ${boxId.value.toString} not found")))
        case Some(box) =>
          if (box.boxCreator.clientId != clientId) { Future.successful(Left(GetNotificationsServiceUnauthorisedResult("clientId does not match boxCreator"))) }
          else notificationsRepository.getByBoxIdAndFilters(boxId, status, fromDateTime, toDateTime).map(Right(_))
      }
  }

  def saveNotification(
      boxId: BoxId,
      notificationId: NotificationId,
      contentType: MessageContentType,
      message: String
    )(implicit hc: HeaderCarrier
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
            case None    => NotificationCreateFailedDuplicateResult("unable to create notification Duplicate found")
          }
      }
  }

}

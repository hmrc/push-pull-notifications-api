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
import play.api.Logger
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.libs.json.Json
import uk.gov.hmrc.pushpullnotificationsapi.connectors.PushConnector
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.API_PUSH_SUBSCRIBER
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.ACKNOWLEDGED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ForwardedHeader, Notification, OutboundNotification}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.NotificationsRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationPushService @Inject()(connector: PushConnector, notificationsRepository: NotificationsRepository){

  def handlePushNotification(subscriber: Subscriber, notification: Notification)(implicit ec: ExecutionContext): Future[Boolean] = {
    if (isValidPushSubscriber(subscriber)) {
      sendNotificationToPush(subscriber.asInstanceOf[PushSubscriber], notification) map {
        case true =>
          notificationsRepository.updateStatus(notification.notificationId, ACKNOWLEDGED)
          true
        case false => false
      }
    } else Future.successful(true)
  }

  private def sendNotificationToPush(subscriber: PushSubscriber, notification: Notification)
                                    (implicit ec: ExecutionContext): Future[Boolean] = {
    val notificationAsJsonString: String = Json.toJson(NotificationResponse.fromNotification(notification)).toString

    val outboundNotification =
      OutboundNotification(subscriber.callBackUrl, List(ForwardedHeader(CONTENT_TYPE, notification.messageContentType.value)), notificationAsJsonString)

    connector.send(outboundNotification).map {
      case _ : PushConnectorSuccessResult => true
      case error: PushConnectorFailedResult =>
        Logger.info(s"Attempt to push to callback URL ${outboundNotification.destinationUrl} failed with error: ${error.throwable.getMessage}")
        false
    }
  }

  private def isValidPushSubscriber(subscriber: Subscriber): Boolean =
    subscriber.subscriptionType.equals(API_PUSH_SUBSCRIBER) && (!subscriber.asInstanceOf[PushSubscriber].callBackUrl.isEmpty)

}

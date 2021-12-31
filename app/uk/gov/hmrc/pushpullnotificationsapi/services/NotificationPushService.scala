/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pushpullnotificationsapi.connectors.PushConnector
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.API_PUSH_SUBSCRIBER
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.ACKNOWLEDGED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ForwardedHeader, Notification, OutboundNotification}
import uk.gov.hmrc.pushpullnotificationsapi.repository.NotificationsRepository
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationPushService @Inject()(connector: PushConnector,
                                        notificationsRepository: NotificationsRepository,
                                        clientService: ClientService,
                                        hmacService: HmacService) extends ApplicationLogger {

  def handlePushNotification(box: Box, notification: Notification)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    if (box.subscriber.isDefined && isValidPushSubscriber(box.subscriber.get)) {
      sendNotificationToPush(box, notification) map {
        case true =>
          notificationsRepository.updateStatus(notification.notificationId, ACKNOWLEDGED).map(_ => 
            logger.info(s"Notification sent successfully for clientId : ${box.boxCreator.clientId.value}")
          )
          true
        case false => {
          logger.info(s"Notification not sent successfully for clientId : ${box.boxCreator.clientId.value}")
          false
        }
      }
    } else Future.successful(true)
  }

  private def sendNotificationToPush(box: Box, notification: Notification)
                                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val subscriber: PushSubscriber = box.subscriber.get.asInstanceOf[PushSubscriber]

    clientService.findOrCreateClient(box.boxCreator.clientId) flatMap { client =>
      val notificationAsJsonString: String = Json.toJson(NotificationResponse.fromNotification(notification)).toString
      val outboundNotification = OutboundNotification(subscriber.callBackUrl,
        calculateForwardedHeaders(client, notificationAsJsonString), notificationAsJsonString)

      connector.send(outboundNotification).map {
        case _ : PushConnectorSuccessResult => true
        case error: PushConnectorFailedResult =>
          logger.info(s"Attempt to push to callback URL ${outboundNotification.destinationUrl} failed with error: ${error.errorMessage}")
          false
      }
    }
  }

  private def isValidPushSubscriber(subscriber: Subscriber): Boolean =
    subscriber.subscriptionType.equals(API_PUSH_SUBSCRIBER) && subscriber.asInstanceOf[PushSubscriber].callBackUrl.nonEmpty

  private def calculateForwardedHeaders(client: Client, notificationAsJsonString: String): List[ForwardedHeader] = {
    val payloadSignature = hmacService.sign(client.secrets.head.value, notificationAsJsonString)
    List(ForwardedHeader("X-Hub-Signature", payloadSignature))
  }
}

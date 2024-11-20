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

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Merge, Source}

import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.API_PUSH_SUBSCRIBER
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.ACKNOWLEDGED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ForwardedHeader, Notification, OutboundNotification, RetryableNotification}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{BoxRepository, NotificationsRepository}
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

@Singleton
class NotificationPushService @Inject() (
    pushService: PushService,
    notificationsRepository: NotificationsRepository,
    boxRepository: BoxRepository,
    clientService: ClientService,
    hmacService: HmacService,
    confirmationService: ConfirmationService,
    metrics: Metrics,
    val clock: Clock
  )(implicit ec: ExecutionContext)
    extends ApplicationLogger with ClockNow {

  def handlePushNotification(box: Box, notification: Notification)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    if (box.subscriber.isDefined && isValidPushSubscriber(box.subscriber.get)) {
      sendNotificationToPush(box, notification) flatMap {
        case true  =>
          notificationsRepository.updateStatus(notification.notificationId, ACKNOWLEDGED).flatMap(_ => {

            val pushDurationInMilliseconds = instant().toEpochMilli - notification.createdDateTime.toEpochMilli()

            metrics.defaultRegistry.timer(s"pushNotifictionDuration.${box.boxId}").update(pushDurationInMilliseconds, TimeUnit.MILLISECONDS)
            // TODO remove this log entry when metrics have been verfied as correct
            logger.info(s"Recording metric pushNotifictionDuration $pushDurationInMilliseconds in millis for Box ID ${box.boxId}")

            metrics.defaultRegistry.counter(s"pushNotifictionSuccessCount.${box.boxId}").inc()

            logger.info(s"Notification sent successfully for clientId: ${box.boxCreator.clientId} for boxId: ${box.boxId} NotificationId:${notification.notificationId}")

            confirmationService.handleConfirmation(notification.notificationId).map(_ => true)
          })
        case false => {
          metrics.defaultRegistry.counter(s"pushNotifictionFailureCount.${box.boxId}").inc()
          logger.info(s"Notification failed to send for clientId: ${box.boxCreator.clientId} for boxId: ${box.boxId} NotificationId:${notification.notificationId}")
          Future.successful(false)
        }
      }
    } else Future.successful(true)
  }

  private def sendNotificationToPush(box: Box, notification: Notification)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val subscriber: PushSubscriber = box.subscriber.get.asInstanceOf[PushSubscriber]

    clientService.findOrCreateClient(box.boxCreator.clientId) flatMap { client =>
      val notificationAsJsonString: String = Json.toJson(NotificationResponse.fromNotification(notification)).toString
      val outboundNotification = OutboundNotification(subscriber.callBackUrl, calculateForwardedHeaders(client, notificationAsJsonString), notificationAsJsonString)

      pushService.handleNotification(outboundNotification).map {
        case _: PushServiceSuccessResult    => true
        case error: PushServiceFailedResult =>
          logger.info(s"Attempt to push to callback URL ${outboundNotification.destinationUrl} failed with error: ${error.errorMessage}")
          false
      }
    }
  }

  private def isValidPushSubscriber(subscriber: Subscriber): Boolean =
    subscriber.subscriptionType == API_PUSH_SUBSCRIBER && subscriber.asInstanceOf[PushSubscriber].callBackUrl.nonEmpty

  private def calculateForwardedHeaders(client: Client, notificationAsJsonString: String): List[ForwardedHeader] = {
    val payloadSignature = hmacService.sign(client.secrets.head.value, notificationAsJsonString)
    List(ForwardedHeader("X-Hub-Signature", payloadSignature))
  }

  def fetchRetryablePushNotifications(retryAfter: Instant): Future[Source[RetryableNotification, NotUsed]] = {
    boxRepository.fetchPushSubscriberBoxes().map { boxes =>
      boxes.map(box => notificationsRepository.fetchRetryableNotifications(box, retryAfter)) match {
        case first :: second :: rest =>
          Source.combine(first, second, rest: _*)(Merge(_))
        case first :: Nil            =>
          first
        case Nil                     =>
          Source.empty
      }
    }
  }
}

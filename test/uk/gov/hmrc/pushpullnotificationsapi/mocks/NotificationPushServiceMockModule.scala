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

package uk.gov.hmrc.pushpullnotificationsapi.mocks

import scala.concurrent.Future.{failed, successful}

import akka.stream.scaladsl.Source
import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.pushpullnotificationsapi.models.Box
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications._
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationPushService

trait NotificationPushServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseNotificationPushServiceMock {

    def aMock: NotificationPushService

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object FetchRetryablePushNotifications {

      def succeedsFor(retryableNotification: RetryableNotification) = {
        when(aMock.fetchRetryablePushNotifications()).thenReturn(successful(Source.future(successful(retryableNotification))))
      }

      def succeedsFor(retryableNotifications: List[RetryableNotification]) = {
        when(aMock.fetchRetryablePushNotifications()).thenReturn(successful(Source(retryableNotifications)))
      }

      def failsWithException() = {
        when(aMock.fetchRetryablePushNotifications())
          .thenReturn(failed(new RuntimeException("Failed")))
      }

      def verifyCalled() = {
        verify(times(1)).fetchRetryablePushNotifications()
      }
    }

    object HandlePushNotification {

      def thenThrowsFor(box: Box, notification: Notification) =
        when(aMock.handlePushNotification(eqTo(box), eqTo(notification))(*, *)).thenReturn(failed(new RuntimeException("BOOM!!!")))

      def returnsTrue() = {
        when(aMock.handlePushNotification(*, *)(*, *)).thenReturn(successful(true))
      }

      def returnsTrueFor(box: Box, notification: Notification) = {
        when(aMock.handlePushNotification(eqTo(box), eqTo(notification))(*, *)).thenReturn(successful(true))
      }

      def returnsFalse() = {
        when(aMock.handlePushNotification(*, *)(*, *)).thenReturn(successful(false))
      }

      def verifyCalled() = {
        verify(times(1)).handlePushNotification(*, *)(*, *)
      }

      def verifyCalledWith(box: Box, notification: Notification) = {
        verify(times(1)).handlePushNotification(eqTo(box), eqTo(notification))(*, *)
      }

      def verifyNeverCalled() = {
        verify(never).handlePushNotification(*, *)(*, *)
      }
    }
  }

  object NotificationPushServiceMock extends BaseNotificationPushServiceMock {
    val aMock = mock[NotificationPushService](withSettings.lenient())
  }
}

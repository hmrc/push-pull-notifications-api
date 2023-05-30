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

package uk.gov.hmrc.pushpullnotificationsapi.mocks.repository

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.mockito.verification.VerificationMode
import uk.gov.hmrc.pushpullnotificationsapi.models.BoxId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.repository.NotificationsRepository

import scala.concurrent.Future.successful

trait NotificationsRepositoryMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseNotificationsRepositoryMock {
    def aMock: NotificationsRepository

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object UpdateRetryAfterDateTime {
      def verifyNeverCalled() = {
        verify(never).updateRetryAfterDateTime(*[NotificationId], *)
      }

      def verifyCalled() = {
        verify.updateRetryAfterDateTime(*[NotificationId], *)
      }

      def returnsSuccessWith(notification: Notification) = {
        when(aMock.updateRetryAfterDateTime(eqTo(notification.notificationId), *)).thenReturn(successful(notification))
      }
    }

    object UpdateStatus {
      def verifyCalledWith(notificationId: NotificationId, status: NotificationStatus) = {
        verify.updateStatus(eqTo(notificationId), eqTo(status))
      }

      def verifyCalled() = {
        verify(atLeastOnce).updateStatus(*[NotificationId], *)
      }

      def succeedsFor(notification: Notification, status: NotificationStatus) = {
        when(aMock.updateStatus(eqTo(notification.notificationId), eqTo(status))).thenReturn(successful(notification))
      }

    }

    object NumberOfNotificationsToReturn {
      def thenReturn(numberToReturn: Int) = {
        when(aMock.numberOfNotificationsToReturn).thenReturn(numberToReturn)

      }

    }
    object SaveNotification {
      def verifyCalled() = {
        verify.saveNotification(*)(*)
      }

      def thenSucceedsWith(result: Option[NotificationId]) = {
        when(aMock.saveNotification(*[Notification])(*)).thenReturn(successful(result))
      }

    }
    object GetByBoxIdAndFilters {
      def verifyCalled() = {
        verify.getByBoxIdAndFilters(*[BoxId], *, *, *, *)(*)
      }

      def succeedsWith(boxId: BoxId, result: List[Notification]) = {
        when(aMock.getByBoxIdAndFilters(eqTo(boxId), *, *, *, *)(*)).thenReturn(successful(result))
      }

    }
    object AcknowledgeNotifications{
      def succeeds() = {
        when(aMock.acknowledgeNotifications(*[BoxId],*)(*)).thenReturn(successful(true))
      }

    }

  }

  object NotificationsRepositoryMock extends BaseNotificationsRepositoryMock {
    val aMock = mock[NotificationsRepository](withSettings.lenient())

  }

}

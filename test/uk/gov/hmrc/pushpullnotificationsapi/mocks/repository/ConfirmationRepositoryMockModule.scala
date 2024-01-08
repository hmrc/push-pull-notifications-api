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

package uk.gov.hmrc.pushpullnotificationsapi.mocks.repository

import scala.concurrent.Future.{failed, successful}

import akka.stream.scaladsl.Source
import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.pushpullnotificationsapi.models.ConfirmationId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ConfirmationStatus, NotificationId}
import uk.gov.hmrc.pushpullnotificationsapi.repository.ConfirmationRepository
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.ConfirmationRequest

trait ConfirmationRepositoryMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseConfirmationRRepositoryMock {
    def aMock: ConfirmationRepository

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object SaveConfirmationRequest {

      def returnsNone() = {
        when(aMock.saveConfirmationRequest(*[ConfirmationRequest])).thenReturn(successful(None))
      }

      def thenSuccessfulWith(confirmationId: ConfirmationId) = {
        when(aMock.saveConfirmationRequest(*[ConfirmationRequest])).thenReturn(successful(Some(confirmationId)))
      }

    }

    object UpdateConfirmationNeed {

      def returnsNone() = {
        when(aMock.updateConfirmationNeed(*[NotificationId])).thenReturn(successful(None))
      }

      def verifyCalled(notificationId: NotificationId) = {
        verify.updateConfirmationNeed(eqTo(notificationId))
      }

      def returnsSuccesswith(notificationId: NotificationId, confirmationRequest: ConfirmationRequest) = {
        when(aMock.updateConfirmationNeed(eqTo(notificationId))).thenReturn(successful(Some(confirmationRequest)))
      }

    }

    object UpdateStatus {

      def failswithException() = {
        when(aMock.updateStatus(*[NotificationId], *)).thenReturn(failed(new RuntimeException("boom")))
      }

      def returnsNone() = {
        when(aMock.updateStatus(*[NotificationId], *)).thenReturn(successful(None))
      }

      def neverCalled() = {
        verify(never).updateStatus(*[NotificationId], *)
      }

      def verifyCalledWith(notificationId: NotificationId, status: ConfirmationStatus) = {
        verify(atMost(1)).updateStatus(eqTo(notificationId), eqTo(status))
      }

      def isSuccessWith(notificationId: NotificationId, status: ConfirmationStatus, confirmationRequest: ConfirmationRequest) = {
        when(aMock.updateStatus(eqTo(notificationId), eqTo(status))).thenReturn(successful(Some(confirmationRequest)))
      }

    }

    object FetchRetryableConfirmations {

      def thenFails() = {
        when(aMock.fetchRetryableConfirmations)
          .thenReturn(Source.future(failed(new RuntimeException("Failed"))))
      }

      def verifyCalledOnce() = {
        verify(atMost(1)).fetchRetryableConfirmations
      }

      def thenSuccessWith(requests: List[ConfirmationRequest]) = {
        when(aMock.fetchRetryableConfirmations).thenReturn(Source(requests))
      }

    }

    object UpdateRetryAfterDateTime {

      def neverCalled() = {
        verify(never).updateRetryAfterDateTime(*[NotificationId], *)
      }

      def verifyCalled() = {
        verify.updateRetryAfterDateTime(*[NotificationId], *)
      }

      def thenSuccessWith(maybeRequest: Option[ConfirmationRequest]) = {
        when(aMock.updateRetryAfterDateTime(*[NotificationId], *)).thenReturn(successful(maybeRequest))
      }

    }

  }

  object ConfirmationRepositoryMock extends BaseConfirmationRRepositoryMock {
    val aMock = mock[ConfirmationRepository](withSettings.lenient())

  }
}

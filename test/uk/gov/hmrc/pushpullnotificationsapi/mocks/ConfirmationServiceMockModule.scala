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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationId
import uk.gov.hmrc.pushpullnotificationsapi.models.{ConfirmationCreateServiceSuccessResult, ConfirmationId}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.ConfirmationRequest
import uk.gov.hmrc.pushpullnotificationsapi.services.ConfirmationService

trait ConfirmationServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseConfirmationServiceMock {

    def aMock: ConfirmationService

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object HandleConfirmation {

      def verifyCalledWith(notificationId: NotificationId) = {
        verify.handleConfirmation(eqTo(notificationId))(*, *)
      }

    }

    object SaveConfirmationRequest {

      def succeeds() = {
        when(aMock.saveConfirmationRequest(*[ConfirmationId], *, *[NotificationId], *)(*)).thenReturn(successful(ConfirmationCreateServiceSuccessResult()))
      }

      def verifyCalled() = {
        verify.saveConfirmationRequest(*[ConfirmationId], *, *[NotificationId], *)(*)
      }
    }

    object SendConfirmation {

      def thenfailsFor(confirmationRequest: ConfirmationRequest) = {
        when(aMock.sendConfirmation(eqTo(confirmationRequest))(*, *)).thenReturn(failed(new RuntimeException("Boom!!!")))
      }

      def thenSuccessFor(confirmationRequest: ConfirmationRequest, result: Boolean = true) = {
        when(aMock.sendConfirmation(eqTo(confirmationRequest))(*, *)).thenReturn(successful(result))
      }

      val slowFuture = () =>
        Future {
          Thread.sleep(100)
          ()
        }

      def thenSuccessWithDelayFor(confirmationRequest: ConfirmationRequest, result: Boolean = true) = {
        when(aMock.sendConfirmation(eqTo(confirmationRequest))(*, *)).thenReturn(slowFuture().map(_ => result))
      }
      /*

       */

      def neverCalled() = {
        verify(never).sendConfirmation(*)(*, *)
      }

      def verifyCalledWith(request: ConfirmationRequest) = {
        verify(atLeastOnce).sendConfirmation(eqTo(request))(*, *)
      }

      def verifyNeverCalledWith(request: ConfirmationRequest) = {
        verify(never).sendConfirmation(eqTo(request))(*, *)
      }

      def verifyCalled() = {
        verify(atMost(1)).sendConfirmation(*)(*, *)
      }

      def thenSuccess(result: Boolean) = {
        when(aMock.sendConfirmation(*)(*, *)).thenReturn(successful(result))
      }

    }

  }

  object ConfirmationServiceMock extends BaseConfirmationServiceMock {
    val aMock = mock[ConfirmationService](withSettings.lenient())

  }

}

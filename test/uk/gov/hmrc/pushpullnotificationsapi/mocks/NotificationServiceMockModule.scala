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

package uk.gov.hmrc.pushpullnotificationsapi.mocks

import java.time.Instant
import scala.concurrent.Future.{failed, successful}

import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationsService

trait NotificationsServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseNotificationsServiceMock {

    def aMock: NotificationsService

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object SaveNotification {

      object XML {

        def succeedsFor(boxId: BoxId, xmlBody: String) = {
          when(aMock.saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*))
            .thenReturn(successful(NotificationCreateSuccessResult()))
        }

        def verifyCalledWith(boxId: BoxId, xmlBody: String) = {
          verify.saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*)
        }

        def failsWithDuplicate(boxId: BoxId, xmlBody: String) = {
          when(aMock.saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*))
            .thenReturn(successful(NotificationCreateFailedDuplicateResult("bang")))
        }

        def failsWithBoxNotFound(boxId: BoxId, xmlBody: String) = {
          when(aMock.saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*))
            .thenReturn(successful(NotificationCreateFailedBoxIdNotFoundResult("some Exception")))
        }

        def throwsFor(boxId: BoxId, xmlBody: String, e: Exception) = {
          when(aMock.saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_XML), eqTo(xmlBody))(*))
            .thenReturn(failed(e))
        }
      }

      object Json {

        def succeedsFor(boxId: BoxId, jsonBody: String) = {
          when(aMock.saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_JSON), eqTo(jsonBody))(*))
            .thenReturn(successful(NotificationCreateSuccessResult()))
        }

        def verifyCalledWith(boxId: BoxId, jsonBody: String) = {
          verify.saveNotification(eqTo(boxId), *[NotificationId], eqTo(MessageContentType.APPLICATION_JSON), eqTo(jsonBody))(*)
        }
      }
    }

    object GetNotifications {

      def succeedsWith(boxId: BoxId, clientId: ClientId, notifications: Notification*) = {
        when(aMock.getNotifications(eqTo(boxId), eqTo(clientId), eqTo(None), eqTo(None), eqTo(None)))
          .thenReturn(successful(Right(notifications.toList)))
      }

      def succeedsWith(boxId: BoxId, status: NotificationStatus, list: List[Notification]) = {
        when(aMock.getNotifications(eqTo(boxId), *[ClientId], eqTo(Some(status)), *, *))
          .thenReturn(successful(Right(list)))
      }

      def failsWithNotFoundFor(boxId: BoxId, status: NotificationStatus, from: Option[Instant], to: Option[Instant]) = {
        when(aMock.getNotifications(eqTo(boxId), *[ClientId], eqTo(Some(status)), eqTo(from), eqTo(to)))
          .thenReturn(successful(Left(GetNotificationsServiceBoxNotFoundResult(""))))
      }

      def failsWithNotFoundFor(boxId: BoxId, status: NotificationStatus) = {
        when(aMock.getNotifications(eqTo(boxId), *[ClientId], eqTo(Some(status)), *, *))
          .thenReturn(successful(Left(GetNotificationsServiceBoxNotFoundResult(""))))
      }

      def failsWithUnauthorisedFor(boxId: BoxId, status: NotificationStatus) = {
        when(aMock.getNotifications(eqTo(boxId), *[ClientId], eqTo(Some(status)), *, *))
          .thenReturn(successful(Left(GetNotificationsServiceUnauthorisedResult(""))))
      }

      def failsWithUnauthorisedFor(boxId: BoxId, status: NotificationStatus, from: Option[Instant], to: Option[Instant]) = {
        when(aMock.getNotifications(eqTo(boxId), *[ClientId], eqTo(Some(status)), eqTo(from), eqTo(to)))
          .thenReturn(successful(Left(GetNotificationsServiceUnauthorisedResult(""))))
      }
    }

    object AcknowledgeNotifications {

      def succeeds() = {
        when(NotificationsServiceMock.aMock.acknowledgeNotifications(*[BoxId], *[ClientId], *)(*))
          .thenReturn(successful(AcknowledgeNotificationsSuccessUpdatedResult(true)))
      }

      def findsNothing() = {
        when(aMock.acknowledgeNotifications(*[BoxId], *[ClientId], *)(*)).thenReturn(successful(AcknowledgeNotificationsServiceBoxNotFoundResult("some message")))
      }

      def isUnauthorised() = {
        when(aMock.acknowledgeNotifications(*[BoxId], *[ClientId], *)(*)).thenReturn(successful(AcknowledgeNotificationsServiceUnauthorisedResult("some message")))
      }
    }
  }

  object NotificationsServiceMock extends BaseNotificationsServiceMock {
    val aMock = mock[NotificationsService](withSettings.lenient())
  }
}

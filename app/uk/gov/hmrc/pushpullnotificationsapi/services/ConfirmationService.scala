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
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.pushpullnotificationsapi.connectors.ConfirmationConnector
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ConfirmationStatus, NotificationId, OutboundConfirmation}
import uk.gov.hmrc.pushpullnotificationsapi.repository.ConfirmationRepository
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.ConfirmationRequest
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger
import java.net.URL

@Singleton
class ConfirmationService @Inject() (repository: ConfirmationRepository, connector: ConfirmationConnector) extends ApplicationLogger {

  def saveConfirmationRequest(
      confirmationId: ConfirmationId,
      confirmationUrl: URL,
      notificationId: NotificationId
    )(implicit ec: ExecutionContext
    ): Future[ConfirmationCreateServiceResult] = {
    repository.saveConfirmationRequest(ConfirmationRequest(confirmationId, confirmationUrl, notificationId)).map {
      case Some(_) => ConfirmationCreateServiceSuccessResult()
      case None    => ConfirmationCreateServiceFailedResult("unable to create confirmation request duplicate found")
    }
  }

  def handleConfirmation(notificationId: NotificationId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    repository.updateConfirmationNeed(notificationId) map {
      case Some(confirmationRequest) =>
        sendConfirmation(confirmationRequest)
        true
      case None                      =>
        logger.trace(s"No confirmation needed for notificationId: ${notificationId.value}")
        false
    }
  }

  def sendConfirmation(request: ConfirmationRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    connector.sendConfirmation(
      request.confirmationUrl,
      OutboundConfirmation(request.confirmationId, request.notificationId, "1", ConfirmationStatus.ACKNOWLEDGED, request.pushedDateTime)
    ) map {
      case _: ConfirmationConnectorSuccessResult =>
        repository.updateStatus(request.notificationId, ConfirmationStatus.ACKNOWLEDGED)
        true
      case _: ConfirmationConnectorFailedResult  =>
        logger.info(s"Confirmation not sent for notificationId: ${request.notificationId.value}")
        false
    }
  }
}

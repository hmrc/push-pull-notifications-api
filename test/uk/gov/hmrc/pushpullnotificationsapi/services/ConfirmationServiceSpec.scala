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

import scala.concurrent.ExecutionContext.Implicits.global

import uk.gov.hmrc.Confirmationpullnotificationsapi.mocks.connectors.ConfirmationConnectorMockModule
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.mocks.repository.ConfirmationRepositoryMockModule
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationStatus, OutboundConfirmation}
import uk.gov.hmrc.pushpullnotificationsapi.models.{ConfirmationCreateServiceFailedResult, ConfirmationCreateServiceSuccessResult}
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

class ConfirmationServiceSpec extends AsyncHmrcSpec with TestData {

  trait SetUp extends ConfirmationRepositoryMockModule with ConfirmationConnectorMockModule {
    val serviceToTest = new ConfirmationService(ConfirmationRepositoryMock.aMock, ConfirmationConnectorMock.aMock)
    implicit val hc = new HeaderCarrier()
  }

  "save Confirmation" should {
    "indicate when successful" in new SetUp {
      ConfirmationRepositoryMock.SaveConfirmationRequest.thenSuccessfulWith(confirmationId)
      val result = await(serviceToTest.saveConfirmationRequest(confirmationId, url, notificationId))
      result shouldBe ConfirmationCreateServiceSuccessResult()
    }

    "indicate when failure" in new SetUp {
      ConfirmationRepositoryMock.SaveConfirmationRequest.returnsNone()
      val result = await(serviceToTest.saveConfirmationRequest(confirmationId, url, notificationId))
      result shouldBe ConfirmationCreateServiceFailedResult("unable to create confirmation request duplicate found")
    }
  }

  "handleConfirmation" should {
    "send Confirmation when update successful" in new SetUp {
      ConfirmationRepositoryMock.UpdateConfirmationNeed.returnsSuccesswith(notificationId, confirmationRequest)
      ConfirmationConnectorMock.SendConfirmation.isSuccessWith(url, OutboundConfirmation(confirmationId, notificationId, "1", acknowledgedStatus, Some(pushedTime)))
      ConfirmationRepositoryMock.UpdateStatus.isSuccessWith(notificationId, NotificationStatus.ACKNOWLEDGED, confirmationRequest)

      await(serviceToTest.handleConfirmation(notificationId)) shouldBe true

      ConfirmationRepositoryMock.UpdateConfirmationNeed.verifyCalled(notificationId)
      ConfirmationConnectorMock.SendConfirmation.verifyCalledWith(url)
      ConfirmationRepositoryMock.UpdateStatus.verifyCalledWith(notificationId, acknowledgedStatus)
    }

    "do nothing when update fails" in new SetUp {
      ConfirmationRepositoryMock.UpdateConfirmationNeed.returnsNone()

      await(serviceToTest.handleConfirmation(notificationId)) shouldBe false

      ConfirmationRepositoryMock.UpdateConfirmationNeed.verifyCalled(notificationId)
      ConfirmationConnectorMock.SendConfirmation.neverCalled()
      ConfirmationRepositoryMock.UpdateStatus.neverCalled()
    }

    //TODO handle the futures in the service correctly this should return false
    "not update the confirmation status when connector fails" in new SetUp {
      ConfirmationRepositoryMock.UpdateConfirmationNeed.returnsSuccesswith(notificationId, confirmationRequest)
      ConfirmationConnectorMock.SendConfirmation.returnsFailure()

      await(serviceToTest.handleConfirmation(notificationId)) shouldBe true

      ConfirmationRepositoryMock.UpdateConfirmationNeed.verifyCalled(notificationId)
      ConfirmationConnectorMock.SendConfirmation.verifyCalledWith(url)
      ConfirmationRepositoryMock.UpdateStatus.neverCalled()
    }

    //TODO handle the futures in the service correctly, this should return false
    "return true when update status fails" in new SetUp {
      ConfirmationRepositoryMock.UpdateConfirmationNeed.returnsSuccesswith(notificationId, confirmationRequest)
      ConfirmationConnectorMock.SendConfirmation.isSuccessWith(url, OutboundConfirmation(confirmationId, notificationId, "1", acknowledgedStatus, Some(pushedTime)))
      ConfirmationRepositoryMock.UpdateStatus.returnsNone()

      await(serviceToTest.handleConfirmation(notificationId)) shouldBe true

      ConfirmationRepositoryMock.UpdateConfirmationNeed.verifyCalled(notificationId)
      ConfirmationConnectorMock.SendConfirmation.verifyCalledWith(url)
      ConfirmationRepositoryMock.UpdateStatus.verifyCalledWith(notificationId, acknowledgedStatus)
    }
  }

  "handleConfirmation" should {
    "return true and call update status on repo when connector successful" in new SetUp {
      ConfirmationConnectorMock.SendConfirmation.isSuccessWith(url, OutboundConfirmation(confirmationId, notificationId, "1", acknowledgedStatus, Some(pushedTime)))
      ConfirmationRepositoryMock.UpdateStatus.isSuccessWith(notificationId, NotificationStatus.ACKNOWLEDGED, confirmationRequest)

      await(serviceToTest.sendConfirmation(confirmationRequest)) shouldBe true

      ConfirmationConnectorMock.SendConfirmation.verifyCalledWith(url)
      ConfirmationRepositoryMock.UpdateStatus.verifyCalledWith(notificationId, acknowledgedStatus)
    }

    "return false and do not call update status on repo when connector fails" in new SetUp {
      ConfirmationConnectorMock.SendConfirmation.returnsFailure()

      await(serviceToTest.sendConfirmation(confirmationRequest)) shouldBe false

      ConfirmationConnectorMock.SendConfirmation.verifyCalledWith(url)
      ConfirmationRepositoryMock.UpdateStatus.neverCalled()
    }

    //TODO handle the future failures in the service correctly, this should return false
    "return true and do not call update status on repo when connector fails" in new SetUp {
      ConfirmationConnectorMock.SendConfirmation.isSuccessWith(url, OutboundConfirmation(confirmationId, notificationId, "1", acknowledgedStatus, Some(pushedTime)))
      ConfirmationRepositoryMock.UpdateStatus.failswithException()

      await(serviceToTest.sendConfirmation(confirmationRequest)) shouldBe true

      ConfirmationConnectorMock.SendConfirmation.verifyCalledWith(url)
      ConfirmationRepositoryMock.UpdateStatus.verifyCalledWith(notificationId, acknowledgedStatus)
    }
  }
}

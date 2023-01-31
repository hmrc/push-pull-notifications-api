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

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.scalatest.BeforeAndAfterEach

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.connectors.ConfirmationConnector
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationId
import uk.gov.hmrc.pushpullnotificationsapi.models.{ConfirmationCreateServiceFailedResult, ConfirmationCreateServiceSuccessResult, ConfirmationId}
import uk.gov.hmrc.pushpullnotificationsapi.repository.ConfirmationRepository

class ConfirmationServiceSpec extends AsyncHmrcSpec with BeforeAndAfterEach {

  private val mockRepo = mock[ConfirmationRepository]
  private val mockConnector = mock[ConfirmationConnector]
  val serviceToTest = new ConfirmationService(mockRepo, mockConnector)
  val confirmationId: ConfirmationId = ConfirmationId(UUID.randomUUID())
  val url = "https://test"
  val notificationId: NotificationId = NotificationId(UUID.randomUUID())

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRepo, mockConnector)
  }

  "save Confirmation" should {
    "indicate when successful" in {
      when(mockRepo.saveConfirmationRequest(*)(*)).thenReturn(Future.successful(Some(confirmationId)))
      val result = await(serviceToTest.saveConfirmationRequest(confirmationId, url, notificationId))
      result shouldBe ConfirmationCreateServiceSuccessResult()
    }

    "indicate when failure" in {
      when(mockRepo.saveConfirmationRequest(*)(*)).thenReturn(Future.successful(None))
      val result = await(serviceToTest.saveConfirmationRequest(confirmationId, url, notificationId))
      result shouldBe ConfirmationCreateServiceFailedResult("unable to create confirmation request duplicate found")
    }
  }
}

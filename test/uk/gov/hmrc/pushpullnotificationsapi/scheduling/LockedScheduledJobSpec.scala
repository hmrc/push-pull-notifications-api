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

package uk.gov.hmrc.pushpullnotificationsapi.scheduling

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import org.mockito.ArgumentMatchers.{any as `*`, eq as eqTo}
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerTest

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.mongo.lock.{Lock, MongoLockRepository}

import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig

// class LockedScheduledJobSpec extends HmrcSpec with ScalaFutures with GuiceOneAppPerTest with BeforeAndAfterEach with FixedClock with MockitoSugar {

//   override def fakeApplication() =
//     new GuiceApplicationBuilder()
//       .configure(
//         "metrics.jvm"     -> false,
//         "metrics.enabled" -> false
//       )
//       .build()

//   trait Setup {

//     // val mockService = mock[SentEmailService]
//     val mockLockRepository   = mock[MongoLockRepository]
//     val mockAppConfig        = mock[AppConfig]
//     when(mockAppConfig.scheduledJobConfig(*)).thenReturn(ScheduledJobConfig(10.seconds, 10.seconds, true))

//     // val subject = new EmailSendingJob(mockAppConfig, mockLockRepository, mockSentEmailService)
//   }

//   "ExclusiveScheduledJob" should {

//     "back off when Mongo lock cannot be obtained" in new Setup {
//       when(mockLockRepository.takeLock(*, *, *)).thenReturn(Future.successful(None))

//       val result = await(subject.execute)

//       result.message shouldBe "Job named EmailSendingJob cannot acquire Mongo lock, not running"
//       verify(mockLockRepository).takeLock(eqTo("EmailSendingJob-lock"), *, *)
//       verify(mockSentEmailService, never).sendNextPendingEmail
//     }

//     "execute in lock when Mongo lock can be obtained" in new Setup {
//       when(mockLockRepository.takeLock(*, *, *)).thenReturn(Future.successful(Some(Lock("", "", instant, instant))))
//       when(mockLockRepository.releaseLock(*, *)).thenReturn(Future.successful(()))
//       when(mockSentEmailService.sendNextPendingEmail).thenReturn(Future("Sent successfully"))

//       val result = await(subject.execute)

//       result.message shouldBe "Job named EmailSendingJob ran, and completed, with result Sent successfully"
//       verify(mockLockRepository).takeLock(eqTo("EmailSendingJob-lock"), *, *)
//       verify(mockLockRepository).releaseLock(eqTo("EmailSendingJob-lock"), *)
//     }
//   }
// }

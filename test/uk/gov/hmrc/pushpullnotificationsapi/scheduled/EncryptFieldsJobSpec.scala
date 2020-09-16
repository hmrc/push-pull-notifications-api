/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.pushpullnotificationsapi.scheduled

import java.util.concurrent.TimeUnit.{HOURS, SECONDS}

import akka.stream.Materializer
import org.joda.time.Duration
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.repository.{ClientRepository, NotificationsRepository}

import scala.concurrent.Future.{failed, successful}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class EncryptFieldsJobSpec extends UnitSpec with MockitoSugar with MongoSpecSupport with GuiceOneAppPerSuite {

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .disable[com.kenshoo.play.metrics.PlayModule]
    .configure("metrics.enabled" -> false).build()
  implicit lazy val materializer: Materializer = app.materializer

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val lockKeeperSuccess: () => Boolean = () => true
    private val reactiveMongoComponent = new ReactiveMongoComponent {
      override def mongoConnector: MongoConnector = mongoConnectorForTest
    }
    val mockLockKeeper: EncryptFieldsJobLockKeeper = new EncryptFieldsJobLockKeeper(reactiveMongoComponent) {
      override def lockId: String = "testLock"
      override def repo: LockRepository = mock[LockRepository]
      override val forceLockReleaseAfter: Duration = Duration.standardMinutes(5) // scalastyle:off magic.number
      override def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
        if (lockKeeperSuccess()) body.map(value => successful(Some(value)))
        else successful(None)
    }

    val encryptFieldsJobConfig: EncryptFieldsJobConfig = EncryptFieldsJobConfig(
      FiniteDuration(60, SECONDS), FiniteDuration(24, HOURS), enabled = true, parallelism = 2)
    val mockNotificationsRepository: NotificationsRepository = mock[NotificationsRepository]
    val mockClientRepository: ClientRepository = mock[ClientRepository]
    val underTest = new EncryptFieldsJob(
      mockLockKeeper,
      encryptFieldsJobConfig,
      mockNotificationsRepository,
      mockClientRepository
    )
  }

  "EncryptFieldsJob" should {
    import scala.concurrent.ExecutionContext.Implicits.global

    "encrypt client secrets and notification messages" in new Setup {
      when(mockNotificationsRepository.encryptNotificationMessages(encryptFieldsJobConfig.parallelism)).thenReturn(successful(()))
      when(mockClientRepository.encryptClientSecrets(encryptFieldsJobConfig.parallelism)).thenReturn(successful(()))

      val result: underTest.Result = await(underTest.execute)

      verify(mockNotificationsRepository, times(1)).encryptNotificationMessages(encryptFieldsJobConfig.parallelism)
      verify(mockClientRepository, times(1)).encryptClientSecrets(encryptFieldsJobConfig.parallelism)
      result.message shouldBe "EncryptFieldsJob Job ran successfully."
    }

    "not execute if the job is already running" in new Setup {
      override val lockKeeperSuccess: () => Boolean = () => false

      val result: underTest.Result = await(underTest.execute)

      verify(mockNotificationsRepository, never).encryptNotificationMessages(encryptFieldsJobConfig.parallelism)
      verify(mockClientRepository, never).encryptClientSecrets(encryptFieldsJobConfig.parallelism)
      result.message shouldBe "EncryptFieldsJob did not run because repository was locked by another instance of the scheduler."
    }

    "handle error when something fails" in new Setup {
      when(mockClientRepository.encryptClientSecrets(encryptFieldsJobConfig.parallelism)).thenReturn(failed(new RuntimeException("Failed")))

      val result: underTest.Result = await(underTest.execute)

      result.message shouldBe "The execution of scheduled job EncryptFieldsJob failed with error 'Failed'. " +
        "The next execution of the job will do retry."
    }
  }
}

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

import akka.stream.Materializer
import com.google.inject.Singleton
import javax.inject.Inject
import org.joda.time.Duration
import play.api.Logger
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{ClientRepository, NotificationsRepository}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
@deprecated("delete after it has encryted the existing data")
class EncryptFieldsJob @Inject()(override val lockKeeper: EncryptFieldsJobLockKeeper,
                                          jobConfig: EncryptFieldsJobConfig,
                                          notificationsRepository: NotificationsRepository,
                                          clientRepository: ClientRepository)
                                         (implicit mat: Materializer) extends ScheduledMongoJob {

  override def name: String = "EncryptFieldsJob"
  override def interval: FiniteDuration = jobConfig.interval
  override def initialDelay: FiniteDuration = jobConfig.initialDelay
  override val isEnabled: Boolean = jobConfig.enabled

  override def runJob(implicit ec: ExecutionContext): Future[RunningOfJobSuccessful] = {
    (for {
      _ <- clientRepository.encryptClientSecrets(jobConfig.parallelism)
      _ <- notificationsRepository.encryptNotificationMessages(jobConfig.parallelism)
    } yield RunningOfJobSuccessful).recoverWith {
      case NonFatal(e) =>
        Logger.error("Failed to encrypt fields", e)
        Future.failed(RunningOfJobFailed(name, e))
    }
  }
}

class EncryptFieldsJobLockKeeper @Inject()(mongo: ReactiveMongoComponent) extends LockKeeper {
  override def repo: LockRepository = new LockRepository()(mongo.mongoConnector.db)

  override def lockId: String = "EncryptFieldsJob"

  override val forceLockReleaseAfter: Duration = Duration.standardMinutes(60) // scalastyle:off magic.number
}

case class EncryptFieldsJobConfig(initialDelay: FiniteDuration, interval: FiniteDuration, enabled: Boolean, parallelism: Int)

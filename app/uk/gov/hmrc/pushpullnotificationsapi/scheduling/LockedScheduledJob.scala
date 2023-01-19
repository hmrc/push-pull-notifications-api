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

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

import org.joda.time.Duration

import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}

trait LockedScheduledJob extends ScheduledJob {

  def executeInLock(implicit ec: ExecutionContext): Future[this.Result]

  val releaseLockAfter: Duration

  val lockRepository: MongoLockRepository
  lazy val lockKeeper: LockService = LockService(lockRepository, lockId = s"$name-scheduled-job-lock", ttl = 1.hour)

  def isRunning: Future[Boolean] = lockRepository.isLocked(lockKeeper.lockId, "owner")

  final def execute(implicit ec: ExecutionContext): Future[Result] =
    lockKeeper.withLock {
      executeInLock
    } map {
      case Some(Result(msg)) => Result(s"Job with $name run and completed with result $msg")
      case None              => Result(s"Job with $name cannot aquire mongo lock, not running")
    }

}

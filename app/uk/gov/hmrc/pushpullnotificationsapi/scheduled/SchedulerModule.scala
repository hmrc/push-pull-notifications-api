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

package uk.gov.hmrc.pushpullnotificationsapi.scheduled

import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration

import com.google.inject.Provider

import play.api.inject.Module
import play.api.{Configuration, Environment}

import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.scheduled.{RetryConfirmationRequestJob, RetryPushNotificationsJob}
import uk.gov.hmrc.pushpullnotificationsapi.scheduling.{ScheduledJobs, ScheduledJobsRunner}

class SchedulerModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[RetryConfirmationRequestJobConfig].toProvider[RetryConfirmationRequestJobConfigProvider],
    bind[RetryPushNotificationsJobConfig].toProvider[RetryPushNotificationsJobConfigProvider],
    bind[ScheduledJobs].toProvider[ScheduledJobsProvider],
    bind[ScheduledJobsRunner].toSelf.eagerly()
  )
}

object SchedulerModule {

  extension (d: Duration) {
    def finite(): FiniteDuration = FiniteDuration(d.toNanos, TimeUnit.NANOSECONDS)
  }
}

@Singleton
class RetryConfirmationRequestJobConfigProvider @Inject() (appConfig: AppConfig) extends Provider[RetryConfirmationRequestJobConfig] {

  override def get(): RetryConfirmationRequestJobConfig = {
    import SchedulerModule.*

    val config = appConfig.scheduledJobConfig("retryConfirmationRequestJob")
    RetryConfirmationRequestJobConfig(
      config.getDuration("initialDelay").finite(),
      config.getDuration("interval").finite(),
      config.getBoolean("enabled"),
      config.getInt("numberOfHoursToRetry"),
      config.getInt("parallelism")
    )
  }
}

@Singleton
class RetryPushNotificationsJobConfigProvider @Inject() (appConfig: AppConfig) extends Provider[RetryPushNotificationsJobConfig] {

  override def get(): RetryPushNotificationsJobConfig = {
    import SchedulerModule.*

    val config = appConfig.scheduledJobConfig("retryPushNotificationsJob")
    RetryPushNotificationsJobConfig(
      config.getDuration("initialDelay").finite(),
      config.getDuration("interval").finite(),
      config.getBoolean("enabled"),
      config.getInt("numberOfHoursToRetry"),
      config.getInt("parallelism")
    )
  }
}

@Singleton
class ScheduledJobsProvider @Inject() (
    retryPushNotificationsJob: RetryPushNotificationsJob,
    retryConfirmationRequestJob: RetryConfirmationRequestJob) extends Provider[ScheduledJobs] {
  override def get(): ScheduledJobs = ScheduledJobs(List(retryPushNotificationsJob, retryConfirmationRequestJob))
}

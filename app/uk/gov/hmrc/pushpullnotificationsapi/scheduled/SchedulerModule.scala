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

import javax.inject.{Inject, Singleton}

import com.google.inject.Provider

import play.api.inject.Module
import play.api.{Configuration, Environment}

import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.scheduled.{RetryConfirmationRequestJob, RetryPushNotificationsJob}
import uk.gov.hmrc.pushpullnotificationsapi.scheduling.{ScheduledJobConfig, ScheduledJobs, ScheduledJobsRunner}

class SchedulerModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[ScheduledJobConfig]
      .qualifiedWith("retryConfirmationRequestJob")
      .toProvider[RetryConfirmationRequestJobConfigProvider],
    bind[ScheduledJobConfig]
      .qualifiedWith("RetryPushNotificationsJob")
      .toProvider[RetryPushNotificationsJobConfigProvider],
    bind[ScheduledJobs].toProvider[ScheduledJobsProvider],
    bind[ScheduledJobsRunner].toSelf.eagerly()
  )
}

@Singleton
class RetryConfirmationRequestJobConfigProvider @Inject() (appConfig: AppConfig) extends Provider[ScheduledJobConfig] {
  def get(): ScheduledJobConfig = appConfig.scheduledJobConfig("retryConfirmationRequestJob")
}

@Singleton
class RetryPushNotificationsJobConfigProvider @Inject() (appConfig: AppConfig) extends Provider[ScheduledJobConfig] {
  def get(): ScheduledJobConfig = appConfig.scheduledJobConfig("retryPushNotificationsJob")
}

@Singleton
class ScheduledJobsProvider @Inject() (
    retryPushNotificationsJob: RetryPushNotificationsJob,
    retryConfirmationRequestJob: RetryConfirmationRequestJob) extends Provider[ScheduledJobs] {
  override def get(): ScheduledJobs = ScheduledJobs(List(retryPushNotificationsJob, retryConfirmationRequestJob))
}

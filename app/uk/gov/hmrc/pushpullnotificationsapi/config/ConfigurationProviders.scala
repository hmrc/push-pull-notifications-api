/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.pushpullnotificationsapi.config

import java.util.concurrent.TimeUnit.{MINUTES, SECONDS}

import javax.inject.{Inject, Provider, Singleton}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.crypto.CompositeSymmetricCrypto
import uk.gov.hmrc.pushpullnotificationsapi.scheduled.RetryPushNotificationsJobConfig
import uk.gov.hmrc.pushpullnotificationsapi.services.LocalCrypto

import scala.concurrent.duration.{Duration, FiniteDuration}

class ConfigurationModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[CompositeSymmetricCrypto].to[LocalCrypto],
      bind[RetryPushNotificationsJobConfig].toProvider[RetryPushNotificationsJobConfigProvider]
    )
  }
}

@Singleton
class RetryPushNotificationsJobConfigProvider  @Inject()(configuration: Configuration)
  extends Provider[RetryPushNotificationsJobConfig] {

  override def get(): RetryPushNotificationsJobConfig = {
    // scalastyle:off magic.number
    val initialDelay = configuration.getOptional[String]("retryPushNotificationsJob.initialDelay").map(Duration.create(_).asInstanceOf[FiniteDuration])
      .getOrElse(FiniteDuration(60, SECONDS))
    val interval = configuration.getOptional[String]("retryPushNotificationsJob.interval").map(Duration.create(_).asInstanceOf[FiniteDuration])
      .getOrElse(FiniteDuration(5, MINUTES))
    val enabled = configuration.getOptional[Boolean]("retryPushNotificationsJob.enabled").getOrElse(false)
    val numberOfHoursToRetry = configuration.getOptional[Int]("retryPushNotificationsJob.numberOfHoursToRetry").getOrElse(6)
    val parallelism = configuration.getOptional[Int]("retryPushNotificationsJob.parallelism").getOrElse(10)
    RetryPushNotificationsJobConfig(initialDelay, interval, enabled, numberOfHoursToRetry, parallelism)
  }
}

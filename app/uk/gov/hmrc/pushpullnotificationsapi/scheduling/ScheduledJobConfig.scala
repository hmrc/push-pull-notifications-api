/*
 * Copyright 2026 HM Revenue & Customs
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

import java.time.Duration
import scala.concurrent.duration.{FiniteDuration, NANOSECONDS}

import com.typesafe.config.Config

case class ScheduledJobConfig(initialDelay: FiniteDuration, interval: FiniteDuration, isEnabled: Boolean, parallelism: Int, numberOfHoursToRetry: Int)

object ScheduledJobConfig {

  extension (d: Duration) {
    def finite(): FiniteDuration = FiniteDuration(d.toNanos, NANOSECONDS)
  }

  def fromConfig(config: Config): ScheduledJobConfig = {
    new ScheduledJobConfig(
      config.getDuration("initialDelay").finite(),
      config.getDuration("interval").finite(),
      config.getBoolean("enabled"),
      config.getInt("parallelism"),
      config.getInt("numberOfHoursToRetry")
    )
  }
}

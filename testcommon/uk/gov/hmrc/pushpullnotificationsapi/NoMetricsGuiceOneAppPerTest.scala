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

package uk.gov.hmrc.pushpullnotificationsapi

import org.scalatest.TestSuite
import org.scalatestplus.play.guice.GuiceOneAppPerTest

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

trait NoMetricsGuiceOneAppPerTest extends GuiceOneAppPerTest {
  self: TestSuite =>

  final override def fakeApplication(): Application =
    builder().build()

  def builder(): GuiceApplicationBuilder = {
    GuiceApplicationBuilder()
      .configure("metrics.jvm" -> false)
  }
}

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

package uk.gov.hmrc.pushpullnotificationsapi.repository.models

import java.time.Instant

import play.api.libs.json.Json

import uk.gov.hmrc.pushpullnotificationsapi.HmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.InstantFormatter._
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._

class JsonFormatterSpec extends HmrcSpec {

  "jsonFormatter" should {

    val dateTime = "2023-02-01T18:18:31.123+0000"

    "only have 3 nano values" in {
      val offsetInstant = lenientFormatter.parse(dateTime, a => Instant.from(a))
      val offsetString = Json.toJson(offsetInstant).as[String]
      offsetString shouldBe dateTime
    }
  }
}

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

package uk.gov.hmrc.pushpullnotificationsapi.services

import scala.util.matching.Regex

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.ClientSecretValue

class ClientSecretGeneratorSpec extends AsyncHmrcSpec {

  trait Setup {
    val underTest = new ClientSecretGenerator
  }

  "generate" should {
    "generate a 32 character secret" in new Setup {
      val secret: ClientSecretValue = underTest.generate

      secret.value should have size 32
    }

    "use alphanumeric characters from base-32" in new Setup {
      val hexPattern: Regex = "^[2-7A-Z]+$".r

      val secret: ClientSecretValue = underTest.generate

      hexPattern.pattern.matcher(secret.value).matches shouldBe true
    }

    "generate different values each time it is called" in new Setup {
      val firstSecret: ClientSecretValue = underTest.generate
      val secondSecret: ClientSecretValue = underTest.generate

      firstSecret should not be secondSecret
    }
  }
}

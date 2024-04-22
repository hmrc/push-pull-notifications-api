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

package uk.gov.hmrc.pushpullnotificationsapi.services

import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec

class ChallengeGeneratorSpec extends HmrcSpec {

  trait Setup {
    val underTest = new ChallengeGenerator()
  }

  "generateChallenge" should {
    "generate a new random challenge every time it is invoked" in new Setup {
      val firstResult: String = underTest.generateChallenge
      val secondResult: String = underTest.generateChallenge

      firstResult should not be secondResult
    }
  }
}

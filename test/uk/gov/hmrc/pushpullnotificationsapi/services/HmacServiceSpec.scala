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

package uk.gov.hmrc.pushpullnotificationsapi.services

import org.mockito.ArgumentMatchersSugar
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

class HmacServiceSpec extends UnitSpec with MockitoSugar with ArgumentMatchersSugar {

  trait Setup {
    val validKey = "the signing key"
    val message = "the message to sign"
    val objInTest = new HmacService()
  }

  "sign" should {
    "return the hmac signature in hexadecimal format" in new Setup {
      val result: String = objInTest.sign(validKey, message)

      result shouldBe "2c20cd60c8d112cceb1ed7314d8e575314c49c16"
    }
  }
}

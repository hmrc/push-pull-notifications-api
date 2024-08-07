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

package uk.gov.hmrc.pushpullnotificationsapi.connectors

import java.util.regex.Pattern
import scala.util.{Failure, Success}

import uk.gov.hmrc.http._

import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec

class OutboundProxyConnectorSpec extends HmrcSpec {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  "validateDestinationUrl" should {
    val destinationUrlPattern: Pattern = "^https.*".r.pattern

    "succeed when the callback URL does match pattern and configured to validate" in {
      val destinationUrl = "https://abc.com/callback"
      val result = OutboundProxyConnector.validateDestinationUrl(true, destinationUrlPattern, List.empty)(destinationUrl)

      result shouldBe Success(destinationUrl)
    }

    "fail when the callback URL does not match pattern and configured to validate" in {
      val destinationUrl = "http://abc.com/callback"
      val expectedErrorMessage = s"Invalid destination URL $destinationUrl"
      val result = OutboundProxyConnector.validateDestinationUrl(true, destinationUrlPattern, List.empty)(destinationUrl)

      result match {
        case Success(_)      => fail()
        case Failure(thrown) => thrown.getMessage() shouldBe expectedErrorMessage
      }
    }

    "succeed when the callback URL does not match pattern but validating url is not enabled" in {
      val destinationUrl = "http://abc.com/callback"
      val result = OutboundProxyConnector.validateDestinationUrl(false, destinationUrlPattern, List.empty)(destinationUrl)

      result shouldBe Success(destinationUrl)
    }

    "succeed when the callback URL matches pattern and is in allowList and configured to validate" in {
      val destinationUrl = "https://abc.com/callback"
      val result = OutboundProxyConnector.validateDestinationUrl(true, destinationUrlPattern, List("abc.com"))(destinationUrl)

      result shouldBe Success(destinationUrl)
    }

    "fail when the callback URL matches pattern but is not in allowList and configured to validate" in {
      val destinationUrl = "https://abc.com/callback"
      val result = OutboundProxyConnector.validateDestinationUrl(true, destinationUrlPattern, List("xyz.com"))(destinationUrl)

      result match {
        case Success(_) => fail()
        case Failure(_) => succeed
      }
    }
  }
}

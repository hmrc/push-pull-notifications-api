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

package uk.gov.hmrc.pushpullnotificationsapi.models

import play.api.libs.json._

import uk.gov.hmrc.apiplatform.modules.utils.JsonFormattersSpec

class WrappedNotificationBodySpec extends JsonFormattersSpec {

  "WrappedNotification" should {
    val aBody = """some text that would be json"""
    val wrappedNotification = WrappedNotificationBody(aBody, "application/json")

    "write json" when {
      "there are no private headers" in {
        testToJsonValues(WrappedNotificationBody(aBody, "application/json"))(
          ("body" -> JsString(aBody)),
          ("contentType" -> JsString("application/json"))
        )
      }

      "there are private headers" in {
        testToJsonValues(wrappedNotification)(
          ("body" -> JsString(aBody)),
          ("contentType" -> JsString("application/json"))
        )
      }
    }

    "read json" when {
      "there are private headers" in {
        testFromJson(s"""{
          "body": "$aBody",
          "contentType": "application/json"
          }""")(wrappedNotification)
      }
    }
  }
}

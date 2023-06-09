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

package uk.gov.hmrc.pushpullnotificationsapi.models

import java.net.URL

import uk.gov.hmrc.apiplatform.modules.utils.JsonFormattersSpec

class WrappedNotificationRequestSpec extends JsonFormattersSpec {

  "WrappedNotificationRequest" should {
    val aBody = """some text that would be json"""
    val confirmationUrl = new URL("http://example.com")

    val wrappedNotificationRequestWithHeaders =
      WrappedNotificationRequest(WrappedNotificationBody(aBody, "application/json"), "1", Some(confirmationUrl), List(PrivateHeader("n1", "v1"), PrivateHeader("n2", "v2")))

    "read json" when {
      "there are private headers" in {
        testFromJson(s"""{
          "notification": {
            "body": "$aBody",
            "contentType": "application/json"
          },
          "version": "1",
          "confirmationUrl": "http://example.com",
          "privateHeaders": [
            {
              "name": "n1",
              "value": "v1"
            },
            {
              "name": "n2",
              "value": "v2"
            }
            ]
          }""")(wrappedNotificationRequestWithHeaders)
      }

      "there are empty private headers" in {
        testFromJson(s"""{
          "notification": {
            "body": "$aBody",
            "contentType": "application/json"
          },
          "version": "1",
          "confirmationUrl": "http://example.com",
          "privateHeaders": []
          }""")(wrappedNotificationRequestWithHeaders.copy(privateHeaders = List.empty))
      }

      "there are no private headers" in {
        testFromJson(s"""{
          "notification": {
            "body": "$aBody",
            "contentType": "application/json"
          },
          "version": "1"
          }""")(wrappedNotificationRequestWithHeaders.copy(confirmationUrl = None, privateHeaders = List.empty))
      }
    }
  }
}

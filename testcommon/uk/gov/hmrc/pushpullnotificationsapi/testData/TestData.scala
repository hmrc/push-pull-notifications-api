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

package uk.gov.hmrc.pushpullnotificationsapi.testData

import play.api.test.Helpers.{ACCEPT, CONTENT_TYPE, USER_AGENT}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, BoxCreator, BoxId, Client, ClientSecretValue, PushSubscriber}

import java.time.Instant
import java.util.UUID

trait TestData {

  val applicationId = ApplicationId.random

  val boxId = BoxId.random
  val clientId: ClientId = ClientId.random
  val clientSecret: ClientSecretValue = ClientSecretValue("someRandomSecret")
  val client: Client = Client(clientId, Seq(clientSecret))
  val boxName: String = "boxName"
  val endpoint = "/iam/a/callbackurl"

  val box: Box = Box(boxId, boxName, BoxCreator(clientId))

  val boxWithExistingPushSubscriber: Box = box.copy(subscriber = Some(PushSubscriber(endpoint, Instant.now)))

  val validAcceptHeader = ACCEPT -> "application/vnd.hmrc.1.0+json"
  val invalidAcceptHeader = ACCEPT -> "application/vnd.hmrc.2.0+json"
  val validContentTypeHeader = CONTENT_TYPE -> "application/json"
  val invalidContentTypeHeader = CONTENT_TYPE -> "text/xml"
  val emptyContentTypeHeader = CONTENT_TYPE -> ""
  val validHeadersWithValidUserAgent: Map[String, String] = Map(CONTENT_TYPE -> "application/json", USER_AGENT -> "api-subscription-fields")

  val validHeadersWithInValidUserAgent: Map[String, String] = Map(validContentTypeHeader, USER_AGENT -> "some-other-service")
  val validHeadersWithInValidContentType: Map[String, String] = Map(invalidContentTypeHeader, USER_AGENT -> "api-subscription-fields")
  val validHeadersWithEmptyContentType: Map[String, String] = Map(emptyContentTypeHeader, USER_AGENT -> "api-subscription-fields")
  val validHeaders: Map[String, String] = Map(validContentTypeHeader, validAcceptHeader)
  val validHeadersJson: Map[String, String] = Map(validAcceptHeader, validContentTypeHeader)
  val validHeadersWithInvalidAcceptHeader: Map[String, String] = Map(invalidAcceptHeader, validContentTypeHeader)
  val validHeadersWithAcceptHeader = List(USER_AGENT -> "api-subscription-fields", ACCEPT -> "application/vnd.hmrc.1.0+json")

}

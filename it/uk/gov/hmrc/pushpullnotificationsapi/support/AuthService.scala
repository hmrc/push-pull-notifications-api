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

package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

trait AuthService {
  val authUrl = "/auth/authorise"
  private val authUrlMatcher = urlEqualTo(authUrl)

  def primeAuthServiceNoClientId(body: String): StubMapping = {
    stubFor(post(authUrlMatcher)
      .withRequestBody(equalToJson(body))
      .withHeader("Authorization", containing("Bearer token"))
      .willReturn(
        aResponse()
          .withStatus(Status.OK)
          .withBody(s"""{
                       |}""".stripMargin)
      ))
  }

  def primeAuthServiceSuccess(clientId: ClientId, body: String): StubMapping = {
    stubFor(post(authUrlMatcher)
      .withRequestBody(equalToJson(body))
      .withHeader("Authorization", containing("Bearer token"))
      .willReturn(
        aResponse()
          .withStatus(Status.OK)
          .withBody(s"""{
                       |"clientId": "${clientId.value}"
                       |}""".stripMargin)
      ))
  }

  def primeAuthServiceFail(): StubMapping = {
    stubFor(post(authUrlMatcher)
      .withHeader("Authorization", containing("Bearer token"))
      .willReturn(
        aResponse()
          .withStatus(Status.UNAUTHORIZED)
      ))
  }
}

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

package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._

trait PushGatewayService {
  val gatewayPostUrl = "/notify"
  val gatewayValidateCalllBackUrl = "/validate-callback"

  def primeGatewayServiceValidateCallBack(status: Int, successfulResult: Boolean = true, errorMessage: Option[String] = None) = {
    val errorMessageStr = errorMessage.fold("")(value => raw""","errorMessage":"${value}"""")
    primeGatewayWithBody(gatewayValidateCalllBackUrl, status, raw"""{"successful":${successfulResult}${errorMessageStr} }""")
  }

  def primeGatewayServiceWithBody(status: Int, successfulResult: Boolean = true) = {
    val body = raw"""{"successful": ${successfulResult} }"""
    primeGatewayWithBody(gatewayPostUrl, status, body)
  }

  def primeGatewayServiceValidateNoBody(status: Int) = {
    primeGatewayServiceNoBody(gatewayValidateCalllBackUrl, status)
  }

  def primeGatewayServicPostNoBody(status: Int) = {
    primeGatewayServiceNoBody(gatewayPostUrl, status)
  }

  private def primeGatewayServiceNoBody(url: String, status: Int) = {

    stubFor(post(urlEqualTo(url))
      .withHeader("Authorization", equalTo("iampushpullapi"))
      .willReturn(
        aResponse()
          .withStatus(status)
      ))
  }

  private def primeGatewayWithBody(url: String, status: Int, body: String) = {

    stubFor(post(urlEqualTo(url))
      .withHeader("Authorization", equalTo("iampushpullapi"))
      .willReturn(
        aResponse()
          .withStatus(status)
          .withHeader("Content-Type", "application/json")
          .withBody(body)
      ))
  }

}

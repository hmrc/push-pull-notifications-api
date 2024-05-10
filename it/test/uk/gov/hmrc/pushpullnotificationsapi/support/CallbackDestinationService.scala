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
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import play.api.libs.json.JsValue
import com.github.tomakehurst.wiremock.stubbing.StubMapping

trait CallbackDestinationService {

  def primeDestinationServiceForCallbackValidation(queryParams: Seq[(String, String)], status: Int, responseBody: Option[JsValue]): StubMapping = {
    val response: ResponseDefinitionBuilder = responseBody
      .fold(aResponse().withStatus(status))(body => aResponse().withStatus(status).withBody(body.toString()))
    val params                              = queryParams.map { case (k, v) => s"$k=$v" }.mkString("?", "&", "")

    stubFor(
      get(urlEqualTo(s"/callback$params"))
        .willReturn(response)
    )
  }

  def primeDestinationServiceForPushNotification(status: Int): Unit = {
      stubFor(
      post(urlEqualTo(s"/callback"))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def verifyCallback(): Unit =  {
    verify(postRequestedFor(urlEqualTo("/callback")))
  }
}
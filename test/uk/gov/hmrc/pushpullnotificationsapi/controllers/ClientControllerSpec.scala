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

package uk.gov.hmrc.pushpullnotificationsapi.controllers

import java.util.UUID.randomUUID

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.toJson
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.services.ClientService
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec

import scala.concurrent.Future
import scala.concurrent.Future.successful

class ClientControllerSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with BeforeAndAfterEach {

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

  val mockClientService: ClientService = mock[ClientService]
  val mockAppConfig: AppConfig = mock[AppConfig]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[ClientService].to(mockClientService))
    .overrides(bind[AppConfig].to(mockAppConfig))
    .build()

  override def beforeEach(): Unit = {
    reset(mockClientService, mockAppConfig)
  }

  val authToken = randomUUID.toString

  private def setUpAppConfig(authHeaderValue: Option[String]): Unit = {
    authHeaderValue match {
      case Some(value) =>
        when(mockAppConfig.authorizationToken).thenReturn(value)
        ()
      case None        => ()
    }
  }

  private def doGet(uri: String, headers: Map[String, String]): Future[Result] = {
    val fakeRequest = FakeRequest(GET, uri).withHeaders(headers.toSeq: _*)
    route(app, fakeRequest).get
  }

  val clientIdStr: String = randomUUID.toString
  val clientId: ClientId = ClientId(clientIdStr)
  val clientSecret: ClientSecret = ClientSecret("someRandomSecret")

  "getClientSecrets" should {
    "return 200 and the array of secrets for the requested client" in {
      setUpAppConfig(Some(authToken))
      when(mockClientService.getClientSecrets(clientId)).thenReturn(successful(Some(Seq(clientSecret))))

      val result = doGet(s"/client/${clientId.value}/secrets", Map("Authorization" -> authToken))

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(Seq(clientSecret))
    }

    "return 404 when there is no matching client for the given client ID" in {
      setUpAppConfig(Some(authToken))
      when(mockClientService.getClientSecrets(clientId)).thenReturn(successful(None))

      val result = doGet(s"/client/${clientId.value}/secrets", Map("Authorization" -> authToken))

      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "code").as[String] shouldBe "CLIENT_NOT_FOUND"
      (contentAsJson(result) \ "message").as[String] shouldBe "Client not found"
    }

    "return 403 when the authorization header does not match the token from the app config" in {
      setUpAppConfig(Some(authToken))
      when(mockClientService.getClientSecrets(clientId)).thenReturn(successful(None))

      val result = doGet(s"/client/${clientId.value}/secrets", Map("Authorization" -> "wrongToken"))

      status(result) shouldBe FORBIDDEN
      (contentAsJson(result) \ "code").as[String] shouldBe "FORBIDDEN"
      (contentAsJson(result) \ "message").as[String] shouldBe "Authorisation failed"
    }
  }
}

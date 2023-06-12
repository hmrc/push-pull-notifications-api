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

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{FORBIDDEN, NOT_FOUND, OK}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbClient
import uk.gov.hmrc.pushpullnotificationsapi.support.{MongoApp, ServerBaseISpec}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.repository.ClientRepository
import uk.gov.hmrc.pushpullnotificationsapi.models.ClientSecretValue
import uk.gov.hmrc.pushpullnotificationsapi.models.Client

class ClientControllerISpec extends ServerBaseISpec with BeforeAndAfterEach with MongoApp[DbClient] {
  this: Suite with ServerProvider =>

  def repo: ClientRepository =
    app.injector.instanceOf[ClientRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
    await(repo.ensureIndexes)
  }

  override protected def repository: PlayMongoRepository[DbClient] = app.injector.instanceOf[ClientRepository]

  private val clientId: ClientId = ClientId.random
  private val clientSecret: ClientSecretValue = ClientSecretValue("someRandomSecret")
  private val client: Client = Client(clientId, Seq(clientSecret))
  private val authToken: String = "authtoken"

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "authorizationKey" -> authToken
      )

  val url = s"http://localhost:$port"
  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def doGet(path: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url$path")
      .withHttpHeaders(headers: _*)
      .get()
      .futureValue

  "GET /client/:clientId/secrets" should {
    "respond with 200 and the array of secrets for the requested client" in {
      await(repo.insertClient(client))

      val result = doGet(s"/client/${clientId.value}/secrets", List("Authorization" -> authToken))

      result.status shouldBe OK
      result.body shouldBe """[{"value":"someRandomSecret"}]"""
    }

    "respond with 404 when there is no matching client for the given client ID" in {
      await(repo.insertClient(client))

      val result = doGet(s"/client/wrongClientId/secrets", List("Authorization" -> authToken))

      result.status shouldBe NOT_FOUND
      result.body shouldBe """{"code":"CLIENT_NOT_FOUND","message":"Client not found"}"""
    }

    "respond with 403 when the authorization header does not match the token from the app config" in {
      await(repo.insertClient(client))

      val result = doGet(s"/client/${clientId.value}/secrets", List("Authorization" -> "wrongToken"))

      result.status shouldBe FORBIDDEN
      result.body shouldBe """{"code":"FORBIDDEN","message":"Authorisation failed"}"""
    }
  }

}

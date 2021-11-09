package uk.gov.hmrc.pushpullnotificationsapi.controllers

import java.util.UUID

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{FORBIDDEN, NOT_FOUND, OK}
import uk.gov.hmrc.pushpullnotificationsapi.models.{Client, ClientId, ClientSecret}
import uk.gov.hmrc.pushpullnotificationsapi.repository.ClientRepository
import uk.gov.hmrc.pushpullnotificationsapi.support.{MongoApp, ServerBaseISpec}

import scala.concurrent.ExecutionContext.Implicits.global

class ClientControllerISpec extends ServerBaseISpec with BeforeAndAfterEach with MongoApp {
  this: Suite with ServerProvider =>

  def repo: ClientRepository =
    app.injector.instanceOf[ClientRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
    await(repo.ensureIndexes)
  }

  private val clientId: ClientId = ClientId(UUID.randomUUID().toString)
  private val clientSecret: ClientSecret = ClientSecret("someRandomSecret")
  private val client: Client = Client(clientId, Seq(clientSecret))
  private val authToken: String = "authtoken"

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "authorizationKey"  -> authToken
      )

  val url = s"http://localhost:8080"
  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def doGet(path: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(s"$url$path")
      .withHttpHeaders(headers: _*)
      .get
      .futureValue

  "GET /client/:clientId/secrets" should {
    "respond with 200 and the array of secrets for the requested client" in {
      await(repo.insertClient(client))

      val result = doGet(s"/client/${clientId.value}/secrets", List("Authorization" -> authToken))

      result.status mustBe OK
      result.body mustBe """[{"value":"someRandomSecret"}]"""
    }

    "respond with 404 when there is no matching client for the given client ID" in {
      await(repo.insertClient(client))

      val result = doGet(s"/client/wrongClientId/secrets", List("Authorization" -> authToken))

      result.status mustBe NOT_FOUND
      result.body mustBe """{"code":"CLIENT_NOT_FOUND","message":"Client not found"}"""
    }

    "respond with 403 when the authorization header does not match the token from the app config" in {
      await(repo.insertClient(client))

      val result = doGet(s"/client/${clientId.value}/secrets", List("Authorization" -> "wrongToken"))

      result.status mustBe FORBIDDEN
      result.body mustBe """{"code":"FORBIDDEN","message":"Authorisation failed"}"""
    }
  }
}

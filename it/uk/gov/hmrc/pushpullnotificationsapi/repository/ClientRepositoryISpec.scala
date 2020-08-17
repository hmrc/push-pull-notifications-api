package uk.gov.hmrc.pushpullnotificationsapi.repository

import java.util.UUID.randomUUID

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.{Client, ClientId, ClientSecret}
import uk.gov.hmrc.pushpullnotificationsapi.support.MongoApp

import scala.concurrent.ExecutionContext.Implicits.global

class ClientRepositoryISpec extends UnitSpec with MongoApp with GuiceOneAppPerSuite {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override implicit lazy val app: Application = appBuilder.build()

  def repo: ClientRepository = app.injector.instanceOf[ClientRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
    await(repo.ensureIndexes)
  }

  val clientId: ClientId = ClientId(randomUUID.toString)
  val clientSecret: ClientSecret = ClientSecret("someRandomSecret")
  val client: Client = Client(clientId, Seq(clientSecret))

  "insertClient" should {
    "insert a client when it does not exist" in {
      await(repo.insertClient(Client(ClientId(randomUUID.toString), Seq(ClientSecret(randomUUID.toString)))))

      val result: Client = await(repo.insertClient(client))

      result shouldBe client
      val fetchedRecords = await(repo.findByClientId(client.id))
      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe client
    }

    "fail when a client with the same ID already exists" in {
      await(repo.insertClient(client))

      val exception: DatabaseException = intercept[DatabaseException] {
        await(repo.insertClient(client))
      }

      exception.getMessage should include ("E11000 duplicate key error collection")
    }
  }

  "findByClientId" should {
    "return matching client" in {
      await(repo.insert(client))

      val result: Option[Client] = await(repo.findByClientId(client.id))

      result shouldBe Some(client)
    }

    "return none when there is no matching client" in {
      await(repo.insert(Client(ClientId(randomUUID.toString), Seq(ClientSecret(randomUUID.toString)))))

      val result: Option[Client] = await(repo.findByClientId(client.id))

      result shouldBe None
    }
  }
}

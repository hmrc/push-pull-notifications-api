package uk.gov.hmrc.pushpullnotificationsapi.repository

import org.mongodb.scala.MongoWriteException
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import java.util.UUID.randomUUID
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}
import uk.gov.hmrc.pushpullnotificationsapi.models.{Client, ClientId, ClientSecret}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbClient
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec

class ClientRepositoryISpec
    extends AsyncHmrcSpec
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with PlayMongoRepositorySupport[DbClient]
    with IntegrationPatience
    with GuiceOneAppPerSuite {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override implicit lazy val app: Application = appBuilder.build()

  def repo: ClientRepository = app.injector.instanceOf[ClientRepository]
  override protected def repository: PlayMongoRepository[DbClient] = app.injector.instanceOf[ClientRepository]

  override def beforeEach() {
    prepareDatabase()
  }

  override protected def afterAll() {
    prepareDatabase()
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

    "encrypt the client secret in the database" in {
      await(repo.insertClient(Client(ClientId(randomUUID.toString), Seq(ClientSecret("the client secret")))))

      await(repo.insertClient(client))

      val dbClients: Seq[DbClient] = await(repo.collection.find().toFuture())

      dbClients.head.secrets.head.encryptedValue shouldBe "X+UILjCREN19DnjPfxBDNECPVWlIUfd76KlrwnleZ/o="
    }

    "fail when a client with the same ID already exists" in {
      await(repo.insertClient(client))

      val exception: MongoWriteException = intercept[MongoWriteException] {
        await(repo.insertClient(client))
      }

      exception.getMessage should include("E11000 duplicate key error collection")
    }
  }

  "findByClientId" should {
    "return matching client" in {
      await(repo.insertClient(client))

      val result: Option[Client] = await(repo.findByClientId(client.id))

      result shouldBe Some(client)
    }

    "return none when there is no matching client" in {
      await(repo.insertClient(Client(ClientId(randomUUID.toString), Seq(ClientSecret(randomUUID.toString)))))

      val result: Option[Client] = await(repo.findByClientId(client.id))

      result shouldBe None
    }
  }
}

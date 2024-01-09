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

package uk.gov.hmrc.pushpullnotificationsapi.repository

import java.util.UUID.randomUUID

import org.mongodb.scala.MongoWriteException
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.PlayMongoRepositorySupport

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.{Client, ClientSecretValue}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbClient

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

  // Do we need there given we have CleanMongoCollectionSupport ??
  override def beforeEach(): Unit = {
    prepareDatabase()
  }

  override protected def afterAll(): Unit = {
    prepareDatabase()
  }

  val clientId: ClientId = ClientId(randomUUID.toString)
  val clientSecret: ClientSecretValue = ClientSecretValue("someRandomSecret")
  val client: Client = Client(clientId, Seq(clientSecret))

  "insertClient" should {
    "insert a client when it does not exist" in {
      await(repo.insertClient(Client(ClientId(randomUUID.toString), Seq(ClientSecretValue(randomUUID.toString)))))

      val result: Client = await(repo.insertClient(client))

      result shouldBe client
      val fetchedRecords = await(repo.findByClientId(client.id))
      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe client
    }

    "encrypt the client secret in the database" in {
      await(repo.insertClient(Client(ClientId(randomUUID.toString), Seq(ClientSecretValue("the client secret")))))

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
      await(repo.insertClient(Client(ClientId(randomUUID.toString), Seq(ClientSecretValue(randomUUID.toString)))))

      val result: Option[Client] = await(repo.findByClientId(client.id))

      result shouldBe None
    }
  }
}

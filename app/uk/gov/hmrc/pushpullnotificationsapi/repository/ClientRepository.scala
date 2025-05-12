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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import com.mongodb.client.model.ReturnDocument
import org.apache.pekko.stream.Materializer
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{FindOneAndUpdateOptions, IndexModel, IndexOptions, Updates}

import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbClient.{fromClient, toClient}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.PlayHmrcMongoFormatters.dbClientFormatter
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.{DbClient, DbClientSecret}
import uk.gov.hmrc.pushpullnotificationsapi.services.LocalCrypto

@Singleton
class ClientRepository @Inject() (mongo: MongoComponent, crypto: LocalCrypto)(implicit ec: ExecutionContext, val mat: Materializer)
    extends PlayMongoRepository[DbClient](
      collectionName = "client",
      mongoComponent = mongo,
      domainFormat = dbClientFormatter,
      indexes = Seq(
        IndexModel(
          ascending("id"),
          IndexOptions()
            .name("id_index")
            .background(true)
            .unique(true)
        )
      ),
      extraCodecs = Seq(
        Codecs.playFormatCodec(ClientId.format),
        Codecs.playFormatCodec(DbClientSecret.format)
      )
    ) {
  override lazy val requiresTtlIndex: Boolean = false

  def insertClient(client: Client): Future[Client] = {
    val dbClient = fromClient(client, crypto)
    collection.findOneAndUpdate(
      filter = equal("id", client.id),
      update = Updates.combine(
        Updates.setOnInsert("id", dbClient.id),
        Updates.setOnInsert("secrets", dbClient.secrets)
      ),
      options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).map(toClient(_, crypto)).head()
  }

  def findByClientId(id: ClientId): Future[Option[Client]] = {
    collection.find(equal("id", id))
      .headOption()
      .map(_.map(toClient(_, crypto)))
  }
}

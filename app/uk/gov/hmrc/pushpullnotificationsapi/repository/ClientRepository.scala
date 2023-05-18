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

package uk.gov.hmrc.pushpullnotificationsapi.repository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import akka.stream.Materializer
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}

import uk.gov.hmrc.crypto.CompositeSymmetricCrypto
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbClient
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbClient.{fromClient, toClient}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.PlayHmrcMongoFormatters.dbClientFormatter
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

@Singleton
class ClientRepository @Inject() (mongo: MongoComponent, crypto: CompositeSymmetricCrypto)(implicit ec: ExecutionContext, val mat: Materializer)
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
      )
    ) {

  def insertClient(client: Client): Future[Client] = {
    collection.insertOne(fromClient(client, crypto)).map(_ => client).head()
  }

  def findByClientId(id: ClientId): Future[Option[Client]] = {
    collection.find(equal("id", Codecs.toBson(id.value)))
      .headOption()
      .map(_.map(toClient(_, crypto)))
  }
}

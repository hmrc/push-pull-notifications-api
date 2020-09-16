/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.akkastream.cursorProducer
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import uk.gov.hmrc.crypto.CompositeSymmetricCrypto
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbClient
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbClient.{fromClient, toClient}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbClientSecret.fromClientSecret
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.ReactiveMongoFormatters.{dbClientFormatter, dbClientSecretFormatter}
import uk.gov.hmrc.pushpullnotificationsapi.util.mongo.IndexHelper.createSingleFieldAscendingIndex

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientRepository @Inject()(mongoComponent: ReactiveMongoComponent, crypto: CompositeSymmetricCrypto)
                                (implicit ec: ExecutionContext, val mat: Materializer)
  extends ReactiveRepository[DbClient, BSONObjectID](
    "client",
    mongoComponent.mongoConnector.db,
    dbClientFormatter,
    ReactiveMongoFormats.objectIdFormats) {

  override def indexes = Seq(
    createSingleFieldAscendingIndex("id", Some("id_index"), isUnique = true)
  )

  def insertClient(client: Client): Future[Client] = {
    collection.insert.one(fromClient(client, crypto)).map(_ => client)
  }

  def findByClientId(id: ClientId): Future[Option[Client]] = {
    find("id" -> id.value).map(_.headOption.map(toClient(_, crypto)))
  }

  @deprecated("delete after scheduled job encryts the existing client secrets")
  def encryptClientSecrets(parallelism: Int): Future[Unit] = {
    def updateSecrets(client: Client) = {
      val encryptedSecrets = Json.toJson(client.secrets.map(fromClientSecret(_, crypto)))
      val updateStatement = Json.obj("$set" -> Json.obj("secrets" -> encryptedSecrets))
      findAndUpdate(Json.obj("id" -> client.id.value), updateStatement, fetchNewObject = false).map(_ => ())
    }

    collection.find(Json.obj(), Option.empty[DbClient]).cursor[DbClient]()
      .documentSource()
      .map(toClient(_, crypto))
      .runWith(Sink.foreachAsync(parallelism)(updateSecrets)).map(_ => ())
  }
}

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

package uk.gov.hmrc.pushpullnotificationsapi.repository.models

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Crypted, PlainText}

import uk.gov.hmrc.pushpullnotificationsapi.models.{Client, ClientSecretValue}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.DbClientSecret.{fromClientSecret, toClientSecret}

case class DbClient(id: ClientId, secrets: Seq[DbClientSecret])

private[repository] object DbClient {

  def fromClient(client: Client, crypto: CompositeSymmetricCrypto): DbClient = {
    DbClient(client.id, client.secrets.map(fromClientSecret(_, crypto)))
  }

  def toClient(dbClient: DbClient, crypto: CompositeSymmetricCrypto): Client = {
    Client(dbClient.id, dbClient.secrets.map(toClientSecret(_, crypto)))
  }
}

private[repository] case class DbClientSecret(encryptedValue: String)

private[repository] object DbClientSecret {

  def fromClientSecret(clientSecret: ClientSecretValue, crypto: CompositeSymmetricCrypto): DbClientSecret = {
    DbClientSecret(crypto.encrypt(PlainText(clientSecret.value)).value)
  }

  def toClientSecret(dbClientSecret: DbClientSecret, crypto: CompositeSymmetricCrypto): ClientSecretValue = {
    ClientSecretValue(crypto.decrypt(Crypted(dbClientSecret.encryptedValue)).value)
  }
}

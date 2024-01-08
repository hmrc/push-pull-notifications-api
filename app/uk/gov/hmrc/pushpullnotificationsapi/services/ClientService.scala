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

package uk.gov.hmrc.pushpullnotificationsapi.services

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models.{Client, ClientSecretValue}
import uk.gov.hmrc.pushpullnotificationsapi.repository.ClientRepository

@Singleton
class ClientService @Inject() (clientRepository: ClientRepository, clientSecretGenerator: ClientSecretGenerator)(implicit ec: ExecutionContext) {

  def getClientSecrets(clientId: ClientId): Future[Option[Seq[ClientSecretValue]]] = {
    clientRepository.findByClientId(clientId).map(_.map(_.secrets))
  }

  def findOrCreateClient(clientId: ClientId): Future[Client] = {
    for {
      clientOption: Option[Client] <- clientRepository.findByClientId(clientId)
      client <- clientOption.fold(clientRepository.insertClient(Client(clientId, Seq(clientSecretGenerator.generate))))(successful)
    } yield client
  }
}

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

import scala.concurrent.ExecutionContext.Implicits.global

import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.mocks.repository.ClientRepositoryMockModule
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.testData.TestData

class ClientServiceSpec extends AsyncHmrcSpec with TestData {

  trait Setup extends ClientRepositoryMockModule {

    val mockClientSecretGenerator: ClientSecretGenerator = mock[ClientSecretGenerator]
    val objInTest = new ClientService(ClientRepositoryMock.aMock, mockClientSecretGenerator)
  }

  "getClientSecrets" should {

    "return ClientSecrets from matching client" in new Setup {
      ClientRepositoryMock.FindByClientId.thenSuccessWith(clientId, Some(client))

      val clientSecrets: Option[Seq[ClientSecretValue]] = await(objInTest.getClientSecrets(clientId))

      clientSecrets shouldBe Some(client.secrets)
    }

    "return none when no client is found" in new Setup {
      ClientRepositoryMock.FindByClientId.thenClientNotFound(clientId)

      val clientSecrets: Option[Seq[ClientSecretValue]] = await(objInTest.getClientSecrets(clientId))

      clientSecrets shouldBe None
    }
  }

  "findOrCreateClient" should {
    "not insert a new client into the client repository when one already exists" in new Setup {
      ClientRepositoryMock.FindByClientId.thenSuccessWith(clientId, Some(client))

      val result: Client = await(objInTest.findOrCreateClient(clientId))

      result shouldBe client
      ClientRepositoryMock.FindByClientId.verifyCalledWith(clientId)

      ClientRepositoryMock.InsertClient.neverCalled()
    }

    "insert a new client into the client repository when none already exist" in new Setup {
      ClientRepositoryMock.FindByClientId.thenClientNotFound(clientId)

      ClientRepositoryMock.InsertClient.thenSuccessfulWith(client)

      when(mockClientSecretGenerator.generate).thenReturn(clientSecret)

      val result: Client = await(objInTest.findOrCreateClient(clientId))

      result shouldBe client
      ClientRepositoryMock.FindByClientId.verifyCalledWith(clientId)
      ClientRepositoryMock.InsertClient.verifyCalledWith(client)
    }
  }
}

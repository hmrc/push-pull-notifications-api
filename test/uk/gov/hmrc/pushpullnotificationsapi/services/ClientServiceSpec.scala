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

package uk.gov.hmrc.pushpullnotificationsapi.services

import java.util.UUID

import org.mockito.ArgumentMatchersSugar
import org.mockito.Mockito.{verify, verifyNoInteractions, verifyNoMoreInteractions, when}
import org.mockito.captor.{ArgCaptor, Captor}
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.connectors.PushConnector
import uk.gov.hmrc.pushpullnotificationsapi.models.SubscriptionType.{API_PULL_SUBSCRIBER, API_PUSH_SUBSCRIBER}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.{BoxRepository, ClientRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ClientServiceSpec extends UnitSpec with MockitoSugar with ArgumentMatchersSugar {


  private val clientIDUUID = UUID.randomUUID().toString
  private val clientId: ClientId = ClientId(clientIDUUID)
  private val clientSecret: ClientSecret = ClientSecret("someRandomSecret")
  private val client: Client = Client(clientId, Seq(clientSecret))

  trait Setup {
    val mockClientRepository: ClientRepository = mock[ClientRepository]
    val objInTest = new ClientService(mockClientRepository)
  }

  "getClientSecrets" should {

    "return ClientSecrets from matching client" in new Setup {
      when(mockClientRepository.findByClientId(clientId)).thenReturn(Future.successful(Some(client)))

      val clientSecrets: Option[Seq[ClientSecret]] = await(objInTest.getClientSecrets(clientId))

      clientSecrets shouldBe Some(client.secrets)
    }

    "return none when no client is found" in new Setup {
      when(mockClientRepository.findByClientId(clientId)).thenReturn(Future.successful(None))

      val clientSecrets: Option[Seq[ClientSecret]] = await(objInTest.getClientSecrets(clientId))

      clientSecrets shouldBe None
    }
  }
}

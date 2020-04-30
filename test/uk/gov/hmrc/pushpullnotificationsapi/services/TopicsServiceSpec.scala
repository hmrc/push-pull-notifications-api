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

import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito.{verify, when}
import org.mockito.ArgumentMatchers.any
import org.mockito.captor.{ArgCaptor, Captor}
import play.api.mvc.Result
import play.api.mvc.Results.{Created, Ok}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.Topic
import uk.gov.hmrc.pushpullnotificationsapi.repository.TopicsRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class TopicsServiceSpec extends UnitSpec with MockitoSugar {

  private val topicId = UUID.randomUUID().toString
  private val clientId: String = "clientId"
  private val topicName: String = "topicName"

  trait Setup {
    val mockRepository: TopicsRepository = mock[TopicsRepository]
    val objInTest = new TopicsService(mockRepository)
    val argumentCaptor: Captor[Topic] = ArgCaptor[Topic]
      when(mockRepository.createTopic(any[Topic])(any[ExecutionContext])).thenReturn(Future.successful(()))

  }

  "TopicsService" should {

    "return Created when topic repo returns true" in new Setup {
      await(objInTest.createTopic(topicId, clientId, topicName))


      verify(mockRepository).createTopic(argumentCaptor.capture)(any[ExecutionContext])
      validateTopic(argumentCaptor.value)
    }

  }

  def validateTopic(topic: Topic): Unit = {
    topic.topicId shouldBe topicId
    topic.topicName shouldBe topicName
    topic.subscribers.size shouldBe 0
    topic.topicCreator.clientId shouldBe clientId
  }
}

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

package uk.gov.hmrc.pushpullnotificationsapi.controllers

import java.util.UUID

import org.mockito.ArgumentMatchersSugar
import org.mockito.Mockito.{reset, verify, verifyNoInteractions, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.HeaderNames.{CONTENT_TYPE, USER_AGENT}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers.{BAD_REQUEST, route, _}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders.AuthAction
import uk.gov.hmrc.pushpullnotificationsapi.models.ReactiveMongoFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.services.{BoxService, ClientService}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class ClientControllerSpec extends UnitSpec with MockitoSugar with ArgumentMatchersSugar
  with GuiceOneAppPerSuite with BeforeAndAfterEach {

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

  val mockClientService: ClientService = mock[ClientService]
  val mockAppConfig: AppConfig = mock[AppConfig]


  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[ClientService].to(mockClientService))
    .overrides(bind[AppConfig].to(mockAppConfig))
    .build()

  override def beforeEach(): Unit = {
    reset(mockClientService)
    reset(mockAppConfig)
  }

  val authToken = "iampushpullapi"
  private def setUpAppConfig( authHeaderValue: Option[String] = None): Unit = {
    authHeaderValue match {
      case Some(value) =>
        when(mockAppConfig.authorizationToken).thenReturn(value)
        ()
      case None => ()
    }
  }

  val clientIdStr: String = UUID.randomUUID().toString
  val clientId: ClientId = ClientId(clientIdStr)

    "getClientSecrets" should {
      "return 201 and boxId when box successfully created" in {
        setUpAppConfig(List("api-subscription-fields"))
        when(mockBoxService.createBox(any[BoxId], any[ClientId], any[String])(any[ExecutionContext]))
          .thenReturn(Future.successful(BoxCreatedResult(boxId)))
        val result = await(doPut("/box", validHeadersWithValidUserAgent, jsonBody))
        status(result) should be(CREATED)
        val expectedBodyStr = s"""{"boxId":"${boxId.value}"}"""
        jsonBodyOf(result) should be(Json.parse(expectedBodyStr))

        verify(mockBoxService).createBox(any[BoxId], eqTo(clientId), eqTo(boxName))(any[ExecutionContext])
      }
    }
}

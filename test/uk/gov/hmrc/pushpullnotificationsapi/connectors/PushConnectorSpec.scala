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

package uk.gov.hmrc.pushpullnotificationsapi.connectors

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{reset, verify, when}
import org.mockito.captor.{ArgCaptor, Captor}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Writes
import play.api.test.Helpers
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.connectors.PushConnector.PushConnectorResponse
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ForwardedHeader, OutboundNotification}
import uk.gov.hmrc.pushpullnotificationsapi.models.{PushConnectorFailedResult, PushConnectorResult, PushConnectorSuccessResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class PushConnectorSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach{
  private val mockHttpClient = mock[HttpClient]
  private val mockAppConfig = mock[AppConfig]
  private implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
  private implicit val hc: HeaderCarrier = HeaderCarrier()
  val outboundUrl = "outboundUrl"
  val outboundUrlAndPath = "outboundUrl/notify"
  val headers = List(ForwardedHeader("header1", "value1"))
  val pushNotification: OutboundNotification = OutboundNotification("someUrl", headers, "{}")

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockHttpClient)
  }

  trait SetUp{
    val gatewayAuthToken = Random.nextString(10) //scalastyle:off magic.number
    when(mockAppConfig.gatewayAuthToken).thenReturn(gatewayAuthToken)

    val connector = new PushConnector(
      mockHttpClient,
      mockAppConfig)
    when(mockAppConfig.outboundNotificationsUrl).thenReturn(outboundUrl)

  }

  "PushConnector" should {
    def httpCallWillSucceedWithResponse(response: PushConnectorResponse) =
      when(mockHttpClient.POST[OutboundNotification, PushConnectorResponse]
        (eqTo(outboundUrlAndPath), any[OutboundNotification](), any[Seq[(String,String)]]())
        (any[Writes[OutboundNotification]](), any[HttpReads[PushConnectorResponse]](), any[HeaderCarrier](), any[ExecutionContext]()))
        .thenReturn(Future.successful(response))

    def httpCallWillFailWithException(exception: Throwable) =
      when(mockHttpClient.POST[OutboundNotification, PushConnectorResponse]
        (eqTo(outboundUrlAndPath), any[OutboundNotification](), any[Seq[(String,String)]]())
        (any[Writes[OutboundNotification]](), any[HttpReads[PushConnectorResponse]](), any[HeaderCarrier](), any[ExecutionContext]()))
        .thenReturn(Future.failed(exception))

    def containsCorrectAuthorizationHeader(headerCarrier: HeaderCarrier, authToken: String) =
      headerCarrier.headers.contains("Authorization" -> authToken) shouldBe true

    "call the gateway correctly and return PushConnectorSuccessResult if notification has been successfully sent" in new SetUp {
      httpCallWillSucceedWithResponse(PushConnectorResponse(true))

      val result: PushConnectorResult = await(connector.send(pushNotification))
      result shouldBe PushConnectorSuccessResult()

      val headerCarrierCaptor: Captor[HeaderCarrier] = ArgCaptor[HeaderCarrier]
      verify(mockHttpClient).POST(eqTo(outboundUrlAndPath), eqTo(pushNotification),
        any[Seq[(String, String)]])(any(),any(), headerCarrierCaptor.capture, any[ExecutionContext])

      containsCorrectAuthorizationHeader(headerCarrierCaptor.value, gatewayAuthToken)
    }

    "call the gateway correctly and return PushConnectorFailedResult if notification has not been successfully sent" in new SetUp {
      httpCallWillSucceedWithResponse(PushConnectorResponse(false))

      val result: PushConnectorResult = await(connector.send(pushNotification))
      result.asInstanceOf[PushConnectorFailedResult].throwable.getMessage shouldBe "PPNS Gateway was unable to successfully deliver notification"

      val headerCarrierCaptor: Captor[HeaderCarrier] = ArgCaptor[HeaderCarrier]

      verify(mockHttpClient).POST(eqTo(outboundUrlAndPath), eqTo(pushNotification),
        any[Seq[(String, String)]])(any(),any(), headerCarrierCaptor.capture, any[ExecutionContext])

      containsCorrectAuthorizationHeader(headerCarrierCaptor.value, gatewayAuthToken)
    }

    "call the gateway correctly and return left when bad request occurs" in new SetUp {
      val exceptionVal = new BadRequestException("Some error")
      httpCallWillFailWithException(exceptionVal)

      val result: PushConnectorResult = await(connector.send(pushNotification))
      result shouldBe PushConnectorFailedResult(exceptionVal)

      verify(mockHttpClient).POST(eqTo(outboundUrlAndPath), eqTo(pushNotification),
        any[Seq[(String, String)]])(any(),any(),any[HeaderCarrier], any[ExecutionContext])
    }

    "call the gateway and return Left when error occurs" in new SetUp {
      val exceptionVal = new IllegalArgumentException("Some error")
      httpCallWillFailWithException(exceptionVal)

      val result: PushConnectorResult = await(connector.send(pushNotification))
      result shouldBe PushConnectorFailedResult(exceptionVal)

      verify(mockHttpClient).POST(eqTo(outboundUrlAndPath), eqTo(pushNotification),
        any[Seq[(String, String)]])(any(),any(),any[HeaderCarrier], any[ExecutionContext])
    }
  }
}

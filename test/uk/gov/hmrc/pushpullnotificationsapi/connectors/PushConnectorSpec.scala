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
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.{CREATED, OK}
import play.api.libs.json.Writes
import play.api.test.Helpers
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ForwardedHeader, OutboundNotification}
import uk.gov.hmrc.pushpullnotificationsapi.models.{PushConnectorFailedResult, PushConnectorResult, PushConnectorSuccessResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

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
    val connector = new PushConnector(
      mockHttpClient,
      mockAppConfig)
    when(mockAppConfig.outboundNotificationsUrl).thenReturn(outboundUrl)

  }

  "PushConnector" should {
    "call the gateway correctly and return right with response when response status 200 is returned" in new SetUp {

      val httpResponse: HttpResponse = mock[HttpResponse]
      when(httpResponse.body).thenReturn("")
      when(httpResponse.status).thenReturn(OK)
      when(mockHttpClient.POST(eqTo(outboundUrlAndPath), any[NodeSeq](), any[Seq[(String,String)]]())(
        any[Writes[NodeSeq]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier](), any[ExecutionContext]()))
        .thenReturn(Future.successful(httpResponse))

      val result: PushConnectorResult = await(connector.send(pushNotification))
      result shouldBe PushConnectorSuccessResult()

      verify(mockHttpClient).POST(eqTo(outboundUrlAndPath), eqTo(pushNotification),
        any[Seq[(String, String)]])(any(),any(),any[HeaderCarrier], any[ExecutionContext])
    }

    "call the gateway correctly and return failed result when response status 201 is returned" in new SetUp {

      val httpResponse: HttpResponse = mock[HttpResponse]
      when(httpResponse.body).thenReturn("")
      when(httpResponse.status).thenReturn(CREATED)
      when(mockHttpClient.POST(eqTo(outboundUrlAndPath), any[NodeSeq](), any[Seq[(String,String)]]())(
        any[Writes[NodeSeq]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier](), any[ExecutionContext]()))
        .thenReturn(Future.successful(httpResponse))

      val result: PushConnectorResult = await(connector.send(pushNotification))

      result.asInstanceOf[PushConnectorFailedResult].throwable.getMessage shouldBe "Unexpected HTTP code 201"
    }

    "call the gateway correctly and return left when bad request occurs" in new SetUp {
      val exceptionVal = new BadRequestException("Some error")

      when(mockHttpClient.POST(eqTo(outboundUrlAndPath), any[NodeSeq](), any[Seq[(String,String)]]())(
        any[Writes[NodeSeq]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier](), any[ExecutionContext]()))
        .thenReturn(Future.failed(exceptionVal))

      val result: PushConnectorResult = await(connector.send(pushNotification))
      result shouldBe PushConnectorFailedResult(exceptionVal)

      verify(mockHttpClient).POST(eqTo(outboundUrlAndPath), eqTo(pushNotification),
        any[Seq[(String, String)]])(any(),any(),any[HeaderCarrier], any[ExecutionContext])
    }

    "call the gateway and return Left when error occurs" in new SetUp {
    val exceptionVal = new IllegalArgumentException("Some error")
      when(mockHttpClient.POST(eqTo(outboundUrlAndPath), any[NodeSeq](), any[Seq[(String,String)]]())(
        any[Writes[NodeSeq]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier](), any[ExecutionContext]()))
        .thenReturn(Future.failed(exceptionVal))

      val result: PushConnectorResult = await(connector.send(pushNotification))
      result shouldBe PushConnectorFailedResult(exceptionVal)
      verify(mockHttpClient).POST(eqTo(outboundUrlAndPath), eqTo(pushNotification),
        any[Seq[(String, String)]])(any(),any(),any[HeaderCarrier], any[ExecutionContext])
    }
  }
}

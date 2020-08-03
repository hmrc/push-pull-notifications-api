package uk.gov.hmrc.pushpullnotificationsapi.connectors

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.OutboundNotification
import uk.gov.hmrc.pushpullnotificationsapi.models.{PushConnectorFailedResult, PushConnectorResult, PushConnectorSuccessResult}
import uk.gov.hmrc.pushpullnotificationsapi.support.{MetricsTestSupport, PushGatewayService, WireMockSupport}
class PushConnectorISpec extends  UnitSpec with WireMockSupport with  GuiceOneAppPerSuite with ScalaFutures with PushGatewayService with MetricsTestSupport  {
  private implicit val hc: HeaderCarrier = HeaderCarrier()



  override def commonStubs(): Unit = {
    givenCleanMetricRegistry()
  }

  override implicit lazy val app: Application = appBuilder.build()

  protected  def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled"                 -> true,
        "auditing.enabled"                -> false,
        "auditing.consumer.baseUri.host"  -> wireMockHost,
        "auditing.consumer.baseUri.port"  -> wireMockPort,
        "microservice.services.push-pull-notifications-gateway.port" -> wireMockPort,
        "microservice.services.push-pull-notifications-gateway.authorizationKey" -> "iampushpullapi"
      )

  trait SetUp {
    val objInTest = app.injector.instanceOf[PushConnector]
  }

  "PushConnector" should {
    "return PushConnectorSuccessResult when OK result and Success true is returned in payload" in new SetUp() {
      primeGatewayServiceWithBody(Status.OK)
      val notification = OutboundNotification("someDestination", List.empty,"{}")
      val result: PushConnectorResult = await(objInTest.send(notification))
      result shouldBe PushConnectorSuccessResult()
    }

    "return PushConnectorFailedResult UnprocessableEntity when OK result and Success false is returned in payload" in new SetUp() {
      primeGatewayServiceWithBody(Status.OK, successfulResult = false)
      val notification = OutboundNotification("someDestination", List.empty,"{}")
      val result: PushConnectorResult = await(objInTest.send(notification))
      result.isInstanceOf[PushConnectorFailedResult] shouldBe true
      val castResult = result.asInstanceOf[PushConnectorFailedResult]
      castResult.throwable.getMessage shouldBe "PPNS Gateway was unable to successfully deliver notification"
    }

    "return PushConnectorFailedResult Notfound when Notfound result" in new SetUp() {
      primeGatewayServiceNoBody(Status.NOT_FOUND)
      val notification = OutboundNotification("someDestination", List.empty,"{}")
      val result: PushConnectorResult = await(objInTest.send(notification))
      result.isInstanceOf[PushConnectorFailedResult] shouldBe true
      val castResult = result.asInstanceOf[PushConnectorFailedResult]

    }
  }

}
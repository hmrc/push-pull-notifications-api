package uk.gov.hmrc.pushpullnotificationsapi.connectors

import java.util.UUID

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, NotificationId, OutboundNotification}
import uk.gov.hmrc.pushpullnotificationsapi.models.{BoxId, NotificationResponse, PushConnectorFailedResult, PushConnectorResult, PushConnectorSuccessResult, UpdateCallbackUrlRequest}
import uk.gov.hmrc.pushpullnotificationsapi.support.{MetricsTestSupport, PushGatewayService, WireMockSupport}
import uk.gov.hmrc.pushpullnotificationsapi.models.ClientId

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
    val notificationResponse: NotificationResponse = NotificationResponse(NotificationId(UUID.randomUUID), BoxId(UUID.randomUUID), MessageContentType.APPLICATION_JSON, "{}")
    val objInTest: PushConnector = app.injector.instanceOf[PushConnector]
  }

  "PushConnector send" should {
    "return PushConnectorSuccessResult when OK result and Success true is returned in payload" in new SetUp() {
      primeGatewayServiceWithBody(Status.OK)
      val notification: OutboundNotification = OutboundNotification("someDestination", notificationResponse)
      val result: PushConnectorResult = await(objInTest.send(notification))
      result shouldBe PushConnectorSuccessResult()
    }

    "return PushConnectorFailedResult UnprocessableEntity when OK result and Success false is returned in payload" in new SetUp() {
      primeGatewayServiceWithBody(Status.OK, successfulResult = false)
      val notification: OutboundNotification = OutboundNotification("someDestination", notificationResponse)
      val result: PushConnectorResult = await(objInTest.send(notification))
      result.isInstanceOf[PushConnectorFailedResult] shouldBe true
      val castResult: PushConnectorFailedResult = result.asInstanceOf[PushConnectorFailedResult]
      castResult.errorMessage shouldBe "PPNS Gateway was unable to successfully deliver notification"
    }

    "return PushConnectorFailedResult Notfound when Notfound result" in new SetUp() {
      primeGatewayServicPostNoBody(Status.NOT_FOUND)
      val notification: OutboundNotification = OutboundNotification("someDestination", notificationResponse)
      val result: PushConnectorResult = await(objInTest.send(notification))
      result.isInstanceOf[PushConnectorFailedResult] shouldBe true
    }
  }

  "PushConnector validate callback" should {

    "return PushConnectorSuccessResult when validate-callback call returns true" in new SetUp() {
      primeGatewayServiceValidateCallBack(Status.OK)
      val request: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(ClientId("clientId"), "calbackUrl","verifyToken" )
      val result = await(objInTest.validateCallbackUrl(request))
      result.isInstanceOf[PushConnectorSuccessResult] shouldBe true
    }

    "return PushConnectorFailedResult when validate-callback call returns false" in new SetUp() {
      primeGatewayServiceValidateCallBack(Status.OK, false, Some("someError"))
      val request: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(ClientId("clientId"), "calbackUrl","verifyToken" )
      val result = await(objInTest.validateCallbackUrl(request))
      result.isInstanceOf[PushConnectorFailedResult] shouldBe true
      val convertedResult = result.asInstanceOf[PushConnectorFailedResult]
      convertedResult.errorMessage shouldBe "someError"
    }

     "return PushConnectorFailedResult when validate-callback call returns false with no error message" in new SetUp() {
      primeGatewayServiceValidateCallBack(Status.OK, false)
      val request: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(ClientId("clientId"), "calbackUrl","verifyToken" )
      val result = await(objInTest.validateCallbackUrl(request))
      result.isInstanceOf[PushConnectorFailedResult] shouldBe true
      val convertedResult = result.asInstanceOf[PushConnectorFailedResult]
      convertedResult.errorMessage shouldBe "Unknown Error"
    }

     "return summat when validate-callback call returns false" in new SetUp() {
      primeGatewayServiceValidateNoBody(Status.BAD_REQUEST)
      val request: UpdateCallbackUrlRequest = UpdateCallbackUrlRequest(ClientId("clientId"), "calbackUrl","verifyToken" )
      val result = await(objInTest.validateCallbackUrl(request))
      result.isInstanceOf[PushConnectorFailedResult] shouldBe true
    }
  }

}

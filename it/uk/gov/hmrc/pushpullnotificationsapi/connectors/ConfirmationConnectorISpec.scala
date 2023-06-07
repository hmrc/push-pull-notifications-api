package uk.gov.hmrc.pushpullnotificationsapi.connectors

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.{ConfirmationConnectorFailedResult, ConfirmationConnectorSuccessResult, ConfirmationId}
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationId, OutboundConfirmation}
import uk.gov.hmrc.pushpullnotificationsapi.support.{MetricsTestSupport, PushGatewayService, WireMockSupport}
import com.github.tomakehurst.wiremock.client.WireMock._

import java.time.Instant
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.ConfirmationStatus

class ConfirmationConnectorISpec extends AsyncHmrcSpec with WireMockSupport with GuiceOneAppPerSuite with PushGatewayService with MetricsTestSupport {
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override def commonStubs(): Unit = givenCleanMetricRegistry()

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "microservice.services.push-pull-notifications-gateway.port" -> wireMockPort
      )

  trait SetUp {
    val objInTest: ConfirmationConnector = app.injector.instanceOf[ConfirmationConnector]
  }

  "Confirmation Connector" should {
    "when it returns 200" in new SetUp() {

      stubFor(post(urlEqualTo("/")).withHeader("Content-Type", equalTo("application/json"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{}")
        ))
      val result = await(
        objInTest.sendConfirmation(
          wireMockBaseUrl,
          OutboundConfirmation(ConfirmationId.random, NotificationId.random, "1", ConfirmationStatus.ACKNOWLEDGED, Some(Instant.now))
        )
      )

      result shouldBe ConfirmationConnectorSuccessResult()
    }

    "when it returns 400" in new SetUp() {

      stubFor(post(urlEqualTo("/"))
        .willReturn(
          aResponse()
            .withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withBody("{}")
        ))
      val result = await(
        objInTest.sendConfirmation(
          wireMockBaseUrl,
          OutboundConfirmation(ConfirmationId.random, NotificationId.random, "1", ConfirmationStatus.ACKNOWLEDGED, Some(Instant.now))
        )
      )

      result shouldBe a[ConfirmationConnectorFailedResult]
    }
  }
}

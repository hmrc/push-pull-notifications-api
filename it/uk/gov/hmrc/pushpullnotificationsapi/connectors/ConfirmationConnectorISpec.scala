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
import uk.gov.hmrc.pushpullnotificationsapi.models.PrivateHeader
import java.time.Instant
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus
import play.api.libs.json.Json

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
          OutboundConfirmation(ConfirmationId.random, NotificationId.random, "1", NotificationStatus.ACKNOWLEDGED, Some(Instant.now), List.empty)
        )
      )

      result shouldBe ConfirmationConnectorSuccessResult()
    }

    "get sent the correct json payload" in new SetUp() {

      val confirmationId = ConfirmationId.random
      val notificationId = NotificationId.random
      val instant = Instant.now()
      val instantAsText = Json.toJson(instant).toString
      val rawText = s"""{"confirmationId":"$confirmationId","notificationId":"${notificationId}","version":"1","status":"ACKNOWLEDGED","dateTime":$instantAsText,"privateHeaders":[{"name":"f1","value":"v1"}]}"""

      stubFor(
        post(urlEqualTo("/"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(equalTo(rawText))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{}")
        ))
      val result = await(
        objInTest.sendConfirmation(
          wireMockBaseUrl,
          OutboundConfirmation(confirmationId, notificationId, "1", NotificationStatus.ACKNOWLEDGED, Some(instant), List(PrivateHeader("f1","v1")))
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
          OutboundConfirmation(ConfirmationId.random, NotificationId.random, "1", NotificationStatus.ACKNOWLEDGED, Some(Instant.now), List.empty)
        )
      )

      result shouldBe a[ConfirmationConnectorFailedResult]
    }
  }
}

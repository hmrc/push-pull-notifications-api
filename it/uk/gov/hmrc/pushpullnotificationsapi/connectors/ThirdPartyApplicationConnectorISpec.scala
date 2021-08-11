package uk.gov.hmrc.pushpullnotificationsapi.connectors

import java.util.UUID

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.test.Helpers._
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.support.{MetricsTestSupport, ThirdPartyApplicationService, WireMockSupport}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec

class ThirdPartyApplicationConnectorISpec
  extends AsyncHmrcSpec with WireMockSupport with GuiceOneAppPerSuite with MetricsTestSupport with ThirdPartyApplicationService {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override def commonStubs(): Unit = givenCleanMetricRegistry()

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "microservice.services.third-party-application.port" -> wireMockPort
      )

  trait SetUp {
    val objInTest: ThirdPartyApplicationConnector = app.injector.instanceOf[ThirdPartyApplicationConnector]
  }

  "getApplicationDetails" should {
    val clientId = "someClientId"

    "retrieve application record based on provided clientId" in new SetUp() {
      val expectedApplicationId = ApplicationId(UUID.randomUUID().toString)
      val jsonResponse: String = raw"""{"id":  "${expectedApplicationId.value}", "clientId": "$clientId"}"""

      primeApplicationQueryEndpoint(OK, jsonResponse, clientId)

      val result: ApplicationResponse = await(objInTest.getApplicationDetails(ClientId(clientId)))

      result.id shouldBe expectedApplicationId
    }


    "return failed Future if TPA returns a 404" in new SetUp {
      primeApplicationQueryEndpoint(NOT_FOUND, "", clientId)

      intercept[UpstreamErrorResponse] {
        await(objInTest.getApplicationDetails(ClientId(clientId)))
      }.statusCode shouldBe NOT_FOUND
    }
  }
}

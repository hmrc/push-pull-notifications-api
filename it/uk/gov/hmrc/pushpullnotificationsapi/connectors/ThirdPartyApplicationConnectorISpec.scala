package uk.gov.hmrc.pushpullnotificationsapi.connectors

import java.util.UUID

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.support.{MetricsTestSupport, ThirdPartyApplicationService, WireMockSupport}

class ThirdPartyApplicationConnectorISpec
  extends UnitSpec with WireMockSupport with GuiceOneAppPerSuite with ScalaFutures with MetricsTestSupport with ThirdPartyApplicationService {

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
      val expectedApplicationId: UUID = UUID.randomUUID()
      val jsonResponse: String = raw"""{"id":  "${expectedApplicationId.toString}", "clientId": "$clientId"}"""

      primeApplicationQueryEndpoint(Status.OK, jsonResponse, clientId)

      val result: ApplicationResponse = await(objInTest.getApplicationDetails(ClientId(clientId))).get

      result.id shouldBe expectedApplicationId
      result.clientId shouldBe clientId
    }

    "return None if TPA returns a 404" in new SetUp {
      primeApplicationQueryEndpoint(Status.NOT_FOUND, "", clientId)

      await(objInTest.getApplicationDetails(ClientId(clientId))) shouldBe None
    }

  }

}

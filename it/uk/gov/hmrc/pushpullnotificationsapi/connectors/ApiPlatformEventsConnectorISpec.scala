package uk.gov.hmrc.pushpullnotificationsapi.connectors

import controllers.Assets.{BAD_REQUEST, CREATED, NOT_FOUND}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.ApplicationId
import uk.gov.hmrc.pushpullnotificationsapi.support.{ApiPlatformEventsService, MetricsTestSupport, WireMockSupport}

class ApiPlatformEventsConnectorISpec
  extends UnitSpec with WireMockSupport with GuiceOneAppPerSuite with ScalaFutures with MetricsTestSupport with ApiPlatformEventsService {

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
        "microservice.services.api-platform-events.port" -> wireMockPort
      )

  trait SetUp {
    val objInTest: ApiPlatformEventsConnector = app.injector.instanceOf[ApiPlatformEventsConnector]
  }

  "sendCallBackUpdatedEvent" should {

    "return true when call to api platform events returns CREATED" in new SetUp {
      primeCallBackUpdatedEndpoint(CREATED)

      val result = await(objInTest.sendCallBackUpdatedEvent(ApplicationId("12344"), "oldUrl", "newUrl"))
      result shouldBe true
    }

    "return false when call to api platform events returns anything other than CREATED" in new SetUp {
      primeCallBackUpdatedEndpoint(BAD_REQUEST)

      val result = await(objInTest.sendCallBackUpdatedEvent(ApplicationId("12344"), "oldUrl", "newUrl"))
      result shouldBe false
    }


  }

}

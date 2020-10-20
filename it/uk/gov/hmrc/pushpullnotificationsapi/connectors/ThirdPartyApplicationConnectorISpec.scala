package uk.gov.hmrc.pushpullnotificationsapi.connectors

import java.util.UUID

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{MessageContentType, NotificationId, OutboundNotification}
import uk.gov.hmrc.pushpullnotificationsapi.support.{MetricsTestSupport, ThirdPartyApplicationService, WireMockSupport}

class ThirdPartyApplicationConnectorISpec extends  UnitSpec with WireMockSupport with  GuiceOneAppPerSuite with ScalaFutures  with MetricsTestSupport with ThirdPartyApplicationService {
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override def commonStubs(): Unit = givenCleanMetricRegistry()

  override implicit lazy val app: Application = appBuilder.build()

  protected  def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled"                 -> true,
        "auditing.enabled"                -> false,
        "auditing.consumer.baseUri.host"  -> wireMockHost,
        "auditing.consumer.baseUri.port"  -> wireMockPort,
        "microservice.services.third-party-application.port" -> wireMockPort
      )

  trait SetUp {
   
    val objInTest: ThirdPartyApplicationConnector = app.injector.instanceOf[ThirdPartyApplicationConnector]
  }

  val clientId  = "someClientId"
  "ThirdPartyApplication Connector send" should {
    
    "do summat" in new SetUp() {
      val expectedApplicationId = UUID.randomUUID().toString()
      val jsonResponse: String = raw"""{"id":  "$expectedApplicationId", "clientId": "$clientId"}"""
      primeService(Status.OK, jsonResponse, clientId)
    
      val result: ApplicationResponse = await(objInTest.getApplicationDetails(ClientId(clientId)))
      result.id.toString shouldBe expectedApplicationId
      result.clientId shouldBe clientId
    }

  } 

}

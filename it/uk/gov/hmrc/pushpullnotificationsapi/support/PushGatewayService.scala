package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._

trait PushGatewayService {
  val gatewayUrl = "/notify"

  private val gatewayUrlMatcher = urlEqualTo(gatewayUrl)


  def primeGatewayService(status : Int)= {
    stubFor(post(gatewayUrlMatcher)
      .withHeader("Authorization", equalTo("iampushpullapi"))
      .willReturn(
        aResponse()
          .withStatus(status)
      )
    )
  }

}

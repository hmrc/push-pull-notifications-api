package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._

trait PushGatewayService {
  val gatewayUrl = "/notify"

  private val gatewayUrlMatcher = urlEqualTo(gatewayUrl)


  def primeGatewayServiceWithBody(status : Int, successfulResult: Boolean = true)= {
    val body = raw"""{"successful": ${successfulResult} }"""
    stubFor(post(gatewayUrlMatcher)
      .withHeader("Authorization", equalTo("iampushpullapi"))
      .willReturn(
        aResponse()
          .withStatus(status)
          .withHeader("Content-Type","application/json")
          .withBody(body)
      )
    )
  }

  def primeGatewayServiceNoBody(status : Int)= {

    stubFor(post(gatewayUrlMatcher)
      .withHeader("Authorization", equalTo("iampushpullapi"))
      .willReturn(
        aResponse()
          .withStatus(status)
      )
    )
  }

}

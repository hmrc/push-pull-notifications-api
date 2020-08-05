package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._

trait PushGatewayService {
  val gatewayPostUrl = "/notify"
  val gatewayValidateCalllBackUrl = "/validate-callback"

  def primeGatewayServiceValidateCallBack(status : Int, successfulResult: Boolean = true) = {
    primeGatewayWithBody(gatewayValidateCalllBackUrl, status, successfulResult)
  }


  def primeGatewayServiceWithBody(status : Int, successfulResult: Boolean = true)= {
   primeGatewayWithBody(gatewayPostUrl, status, successfulResult)
  }

  def primeGatewayServiceValidateNoBody(status: Int) = {
    primeGatewayServiceNoBody(gatewayValidateCalllBackUrl, status)
  }

    def primeGatewayServicPostNoBody(status: Int) = {
    primeGatewayServiceNoBody(gatewayPostUrl, status)
  }

  private def primeGatewayServiceNoBody(url: String, status : Int)= {

    stubFor(post(urlEqualTo(url))
      .withHeader("Authorization", equalTo("iampushpullapi"))
      .willReturn(
        aResponse()
          .withStatus(status)
      )
    )
  }

   private def primeGatewayWithBody(url: String, status : Int, successfulResult: Boolean = true)= {
    val body = raw"""{"successful": ${successfulResult} }"""
    stubFor(post(urlEqualTo(url))
      .withHeader("Authorization", equalTo("iampushpullapi"))
      .willReturn(
        aResponse()
          .withStatus(status)
          .withHeader("Content-Type","application/json")
          .withBody(body)
      )
    )
  }

}

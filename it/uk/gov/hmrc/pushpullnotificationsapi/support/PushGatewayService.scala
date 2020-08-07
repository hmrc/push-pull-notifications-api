package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._

trait PushGatewayService {
  val gatewayPostUrl = "/notify"
  val gatewayValidateCalllBackUrl = "/validate-callback"

  def primeGatewayServiceValidateCallBack(status : Int, successfulResult: Boolean = true, errorMessage: Option[String] = None) = {
    val errorMessageStr = errorMessage.fold("")(value => raw""","errorMessage":"${value}"""")
    primeGatewayWithBody(gatewayValidateCalllBackUrl, status, successfulResult, raw"""{"successful":${successfulResult}${errorMessageStr} }""")
  }


  def primeGatewayServiceWithBody(status : Int, successfulResult: Boolean = true)= {
     val body = raw"""{"successful": ${successfulResult} }"""
   primeGatewayWithBody(gatewayPostUrl, status, successfulResult, body)
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

   private def primeGatewayWithBody(url: String, status : Int, successfulResult: Boolean = true, body: String)= {
   
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

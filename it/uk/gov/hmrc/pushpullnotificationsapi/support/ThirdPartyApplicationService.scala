package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._

trait ThirdPartyApplicationService {
  val queryApplicationsUrl = "/application"


  def primeApplicationQueryEndpoint(status : Int, body: String, clientId: String)= {
    stubFor(get(urlPathEqualTo(queryApplicationsUrl))
      .withQueryParam("clientId", equalTo(clientId))
      .willReturn(
        aResponse()
        .withBody(body)
        .withStatus(status)
      )
    )
  }

}

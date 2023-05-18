package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

trait ThirdPartyApplicationService {
  val queryApplicationsUrl = "/application"

  def primeApplicationQueryEndpoint(status: Int, body: String, clientId: ClientId) = {
    stubFor(get(urlPathEqualTo(queryApplicationsUrl))
      .withQueryParam("clientId", equalTo(clientId.value.toString))
      .willReturn(
        aResponse()
          .withBody(body)
          .withStatus(status)
      ))
  }

}

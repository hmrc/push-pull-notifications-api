package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._

trait ApiPlatformEventsService {
  val callbackUpdatedEventUri = "/application-events/ppnsCallbackUriUpdated"

  def primeCallBackUpdatedEndpoint(responseStatus: Int) = {
    stubFor(post(urlPathEqualTo(callbackUpdatedEventUri))
      .willReturn(
        aResponse()
          .withStatus(responseStatus)
      ))
  }

}

package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status

trait AuthService {
  val authUrl = "/auth/authorise"
  private val authUrlMatcher = urlEqualTo(authUrl)


  def primeAuthServiceNoCLientId( body: String): StubMapping = {
    stubFor(post(authUrlMatcher)
      .withRequestBody(equalToJson(body))
      .willReturn(
        aResponse()
          .withStatus(Status.OK)
          .withBody(s"""{
                       |}""".stripMargin)
      )
    )
  }

  def primeAuthServiceSuccess(clientId: String, body: String): StubMapping = {
    stubFor(post(authUrlMatcher)
      .withRequestBody(equalToJson(body))
      .willReturn(
        aResponse()
          .withStatus(Status.OK)
          .withBody(s"""{
            |"optionalClientId": "$clientId"
            |}""".stripMargin)
      )
    )
  }

  def primeAuthServiceFail(): StubMapping = {
    stubFor(post(authUrlMatcher)
      .willReturn(
        aResponse()
          .withStatus(Status.UNAUTHORIZED)

      )
    )
  }
}

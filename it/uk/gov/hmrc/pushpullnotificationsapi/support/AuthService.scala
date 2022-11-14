package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status

trait AuthService {
  val authUrl = "/auth/authorise"
  private val authUrlMatcher = urlEqualTo(authUrl)


  def primeAuthServiceNoClientId( body: String): StubMapping = {
    stubFor(post(authUrlMatcher)
      .withRequestBody(equalToJson(body))
      .withHeader("Authorization", containing("Bearer token"))
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
      .withHeader("Authorization", containing("Bearer token"))
      .willReturn(
        aResponse()
          .withStatus(Status.OK)
          .withBody(s"""{
            |"clientId": "$clientId"
            |}""".stripMargin)
      )
    )
  }

  def primeAuthServiceFail(): StubMapping = {
    stubFor(post(authUrlMatcher)
      .withHeader("Authorization", containing("Bearer token"))
      .willReturn(
        aResponse()
          .withStatus(Status.UNAUTHORIZED)

      )
    )
  }
}

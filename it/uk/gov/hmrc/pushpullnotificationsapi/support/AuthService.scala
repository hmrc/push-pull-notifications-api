package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalToJson, post, stubFor, urlEqualTo}
import play.api.http.Status
import play.api.test.Helpers.{AUTHORIZATION, WWW_AUTHENTICATE}

trait AuthService {
  val authUrl = "/auth/authorise"
  private val authUrlMatcher = urlEqualTo(authUrl)

  def primeAuthServiceSuccess(clientId: String, body: String)= {
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

  def primeAuthServiceFail()= {
    stubFor(post(authUrlMatcher)
      .willReturn(
        aResponse()
          .withStatus(Status.UNAUTHORIZED)

      )
    )
  }
}

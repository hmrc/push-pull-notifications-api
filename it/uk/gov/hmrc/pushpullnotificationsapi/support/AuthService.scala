package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

trait AuthService {
  val authUrl = "/auth/authorise"
  private val authUrlMatcher = urlEqualTo(authUrl)

  def primeAuthServiceNoClientId(body: String): StubMapping = {
    stubFor(post(authUrlMatcher)
      .withRequestBody(equalToJson(body))
      .withHeader("Authorization", containing("Bearer token"))
      .willReturn(
        aResponse()
          .withStatus(Status.OK)
          .withBody(s"""{
                       |}""".stripMargin)
      ))
  }

  def primeAuthServiceSuccess(clientId: ClientId, body: String): StubMapping = {
    stubFor(post(authUrlMatcher)
      .withRequestBody(equalToJson(body))
      .withHeader("Authorization", containing("Bearer token"))
      .willReturn(
        aResponse()
          .withStatus(Status.OK)
          .withBody(s"""{
                       |"clientId": "${clientId.value}"
                       |}""".stripMargin)
      ))
  }

  def primeAuthServiceFail(): StubMapping = {
    stubFor(post(authUrlMatcher)
      .withHeader("Authorization", containing("Bearer token"))
      .willReturn(
        aResponse()
          .withStatus(Status.UNAUTHORIZED)
      ))
  }
}

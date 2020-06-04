package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status
import play.api.test.Helpers.WWW_AUTHENTICATE

trait AuditService {
  val auditUrl = "/write/audit"
  val auditMergedUrl = "/write/audit/merged"
  private val auditUrlMAtcher = urlEqualTo(auditUrl)
  private val auditMergedUrlMAtcher = urlEqualTo(auditMergedUrl)

  def primeAuditService()= {
    stubFor(post(auditUrlMAtcher)
      .willReturn(
        aResponse()
          .withStatus(Status.NO_CONTENT)
      )
    )

    stubFor(post(auditMergedUrlMAtcher)
      .willReturn(
        aResponse()
          .withStatus(Status.NO_CONTENT)
      )
    )
  }
}

package uk.gov.hmrc.pushpullnotificationsapi.support

import play.api.inject.guice.GuiceApplicationBuilder

trait TestApplication {
  _: BaseISpec =>


  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled"                 -> true,
        "auditing.enabled"                -> true,
        "auditing.consumer.baseUri.host"  -> wireMockHost,
        "auditing.consumer.baseUri.port"  -> wireMockPort
      )

}

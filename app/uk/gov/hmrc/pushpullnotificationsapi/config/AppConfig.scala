/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.pushpullnotificationsapi.config

import javax.inject.{Inject, Singleton}
import scala.collection.JavaConverters._

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: ServicesConfig) {
  val notificationTTLinSeconds: Long = config.get[Long]("notifications.ttlinseconds")
  val numberOfNotificationsToRetrievePerRequest: Int = config.get[Int]("notifications.numberToRetrievePerRequest")

  val outboundNotificationsUrl = servicesConfig.baseUrl("push-pull-notifications-gateway")
  val gatewayAuthToken: String = config.get[String]("microservice.services.push-pull-notifications-gateway.authorizationKey")

  val apiPlatformEventsUrl = servicesConfig.baseUrl("api-platform-events")
  val thirdPartyApplicationUrl = servicesConfig.baseUrl("third-party-application")
  val authBaseUrl: String = servicesConfig.baseUrl("auth")
  val auditingEnabled: Boolean = config.get[Boolean]("auditing.enabled")
  val graphiteHost: String = config.get[String]("microservice.metrics.graphite.host")

  val allowlistedUserAgentList: List[String] = config.underlying.getStringList("allowlisted.useragents").asScala.toList

  val apiStatus = config.get[String]("apiStatus")
  val cmbEnabled: Boolean = config.get[Boolean]("cmb.enabled")
  val authorizationToken: String = config.get[String]("authorizationKey")
  val mongoEncryptionKey: String = config.get[String]("mongodb.encryption.key")

  val maxNotificationSize = config.underlying.getBytes("notifications.maxSize")
  val wrappedNotificationEnvelopeSize = config.underlying.getBytes("notifications.envelopeSize")
}

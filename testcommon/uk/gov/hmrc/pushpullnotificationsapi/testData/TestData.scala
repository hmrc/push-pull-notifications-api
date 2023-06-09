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

package uk.gov.hmrc.pushpullnotificationsapi.testData

import java.net.URL
import java.time.{Duration, Instant}
import java.util.UUID

import play.api.test.Helpers.{ACCEPT, CONTENT_TYPE, USER_AGENT}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.FAILED
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{ConfirmationStatus, MessageContentType, Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.ConfirmationRequest

trait TestData {

  val applicationId = ApplicationId.random

  val boxId = BoxId.random
  val clientId: ClientId = ClientId.random
  val clientSecret: ClientSecretValue = ClientSecretValue("someRandomSecret")
  val client: Client = Client(clientId, Seq(clientSecret))
  val boxName: String = "boxName"
  val endpoint = "/iam/a/callbackurl"

  val BoxObjectWithNoSubscribers = Box(boxId, boxName, BoxCreator(clientId))

  val boxWithExistingPushSubscriber: Box = BoxObjectWithNoSubscribers.copy(subscriber = Some(PushSubscriber(endpoint, Instant.now)))

  val validAcceptHeader = ACCEPT -> "application/vnd.hmrc.1.0+json"
  val invalidAcceptHeader = ACCEPT -> "application/vnd.hmrc.2.0+json"
  val validContentTypeHeader = CONTENT_TYPE -> "application/json"
  val invalidContentTypeHeader = CONTENT_TYPE -> "text/xml"
  val emptyContentTypeHeader = CONTENT_TYPE -> ""
  val validHeadersWithValidUserAgent: Map[String, String] = Map(CONTENT_TYPE -> "application/json", USER_AGENT -> "api-subscription-fields")

  val validHeadersWithInValidUserAgent: Map[String, String] = Map(validContentTypeHeader, USER_AGENT -> "some-other-service")
  val validHeadersWithInValidContentType: Map[String, String] = Map(invalidContentTypeHeader, USER_AGENT -> "api-subscription-fields")
  val validHeadersWithEmptyContentType: Map[String, String] = Map(emptyContentTypeHeader, USER_AGENT -> "api-subscription-fields")
  val validHeaders: Map[String, String] = Map(validContentTypeHeader, validAcceptHeader)
  val validHeadersJson: Map[String, String] = Map(validAcceptHeader, validContentTypeHeader)
  val validHeadersWithInvalidAcceptHeader: Map[String, String] = Map(invalidAcceptHeader, validContentTypeHeader)
  val validHeadersWithAcceptHeader = List(USER_AGENT -> "api-subscription-fields", ACCEPT -> "application/vnd.hmrc.1.0+json")

  val confirmationId: ConfirmationId = ConfirmationId(UUID.randomUUID())
  val url = new URL("https://test")
  val notificationId: NotificationId = NotificationId(UUID.randomUUID())
  val pushedTime = Instant.now()
  val acknowledgedNotificationStatus = NotificationStatus.ACKNOWLEDGED
  val pendingNotificationStatus = NotificationStatus.PENDING

  val acknowledgedConfirmationStatus = ConfirmationStatus.ACKNOWLEDGED
  val pendingConfirmationStatus = ConfirmationStatus.PENDING

  val messageContentTypeJson = MessageContentType.APPLICATION_JSON
  val messageContentTypeXml = MessageContentType.APPLICATION_XML

  val confirmationRequest = ConfirmationRequest(confirmationId, url, notificationId, List.empty, pushedDateTime = Some(pushedTime))

  val outOfDateConfirmationRequest: ConfirmationRequest =
    ConfirmationRequest(confirmationId = confirmationId, new URL("https://anotherurl.com"), notificationId, List.empty, createdDateTime = Instant.now.minus(Duration.ofHours(7)))

  val pushSubscriber = PushSubscriber("mycallbackUrl")
  val pullSubscriber = PullSubscriber("")

  val BoxObjectWithPushSubscribers = Box(boxId, boxName, BoxCreator(clientId), subscriber = Some(pushSubscriber))
  val BoxObjectWithPullSubscribers = Box(boxId, boxName, BoxCreator(clientId), subscriber = Some(pullSubscriber))

  val message = "message"

  val notification: Notification =
    Notification(
      notificationId,
      BoxId(UUID.randomUUID()),
      MessageContentType.APPLICATION_JSON,
      """{ "foo": "bar" }""",
      NotificationStatus.PENDING
    )

  val failedNotification = notification.copy(status = FAILED)

  def notificationWithRetryAfter(retryAfter: Instant): Notification = Notification(
    NotificationId(UUID.randomUUID()),
    BoxId(UUID.randomUUID()),
    MessageContentType.APPLICATION_JSON,
    "{}",
    NotificationStatus.FAILED,
    retryAfter
  )

}

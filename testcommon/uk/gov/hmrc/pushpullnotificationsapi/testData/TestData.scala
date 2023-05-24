package uk.gov.hmrc.pushpullnotificationsapi.testData

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, BoxCreator, BoxId, Client, ClientSecretValue, PushSubscriber}

import java.time.Instant
import java.util.UUID

trait TestData {

  val applicationId = ApplicationId.random

  val boxId = BoxId.random
  val clientId: ClientId = ClientId.random
  val clientSecret: ClientSecretValue = ClientSecretValue("someRandomSecret")
  val client: Client = Client(clientId, Seq(clientSecret))
  val boxName: String = "boxName"
  val endpoint = "/iam/a/callbackurl"

  val box: Box = Box(boxId, boxName, BoxCreator(clientId))

  val boxWithExistingSubscriber: Box = box.copy(subscriber = Some(PushSubscriber(endpoint, Instant.now)))


}

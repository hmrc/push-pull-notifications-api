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

package uk.gov.hmrc.pushpullnotificationsapi.repository

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.MessageContentType.APPLICATION_JSON

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.Notification

import java.time.temporal.ChronoUnit
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.RetryableNotification
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus

import java.time.Duration
import akka.stream.scaladsl.Sink
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.pushpullnotificationsapi.services.NotificationPushService
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

class BoxRepositoryISpec
    extends AsyncHmrcSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with CleanMongoCollectionSupport
    with GuiceOneAppPerSuite
    with IntegrationPatience
    with Matchers
    with PlayMongoRepositorySupport[Box] {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override implicit lazy val app: Application = appBuilder.build()
  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

  def repo: BoxRepository = app.injector.instanceOf[BoxRepository]
  def notificationsRepo: NotificationsRepository = app.injector.instanceOf[NotificationsRepository]

  def notificationPushService = app.injector.instanceOf[NotificationPushService]

  override protected def repository: PlayMongoRepository[Box] = app.injector.instanceOf[BoxRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  val clientId: ClientId = ClientId.random
  val boxName: String = "boxName"
  final val boxId: BoxId = BoxId.random
  val callBackEndpoint = "some/endpoint"
  val box: Box = Box(boxName = boxName, boxId = boxId, boxCreator = BoxCreator(clientId))

  def createNotificationInDB(
      status: NotificationStatus = PENDING,
      createdDateTime: Instant = Instant.now,
      notificationId: NotificationId = NotificationId.random,
      retryAfterDateTime: Option[Instant] = None,
      boxId: BoxId = boxId
    ): Notification = {
    val notification = Notification(
      notificationId,
      boxId,
      APPLICATION_JSON,
      message = "{\"someJsone\": \"someValue\"}",
      status = status,
      createdDateTime = createdDateTime,
      retryAfterDateTime = retryAfterDateTime
    )

    val save = await(notificationsRepo.saveNotification(notification))
    if (save.isEmpty) throw new RuntimeException("Oh dear")
    notification
  }

  "createBox" should {

    "create a Box with one PushSubscriber" in {
      val result: Unit = await(repo.createBox(box))
      result shouldBe ((): Unit)
      val fetchedRecords = await(repo.collection.find().toFuture())
      val fetchedBox = fetchedRecords.head
      fetchedBox.boxName shouldBe boxName
      fetchedBox.boxCreator.clientId shouldBe clientId
      fetchedBox.boxId shouldBe box.boxId
      fetchedBox.subscriber.isDefined shouldBe false
    }

    "create a Box should allow boxes for same clientId but different BoxNames" in {
      val result = await(repo.createBox(box)).asInstanceOf[BoxCreatedResult]
      result.box.boxId shouldBe boxId
      val newBoxId = BoxId.random
      val newbox = box.copy(newBoxId, boxName = "someNewName")
      val result2 = await(repo.createBox(newbox)).asInstanceOf[BoxCreatedResult]
      result2.box shouldBe newbox
      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 2
    }

    "create a Box should allow a box for different clientId but same BoxNames" in {
      val result: Unit = await(repo.createBox(box))
      result shouldBe ((): Unit)
      val newBoxId = BoxId.random
      val newBox = box.copy(newBoxId, boxCreator = box.boxCreator.copy(ClientId(UUID.randomUUID().toString)))
      val result2: Unit = await(repo.createBox(newBox))
      result2 shouldBe ((): Unit)
      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 2
    }

    "create a Box should return BoxCreateFailedResult and only create a box once when box with same name and client id called twice" in {
      await(repo.createBox(box))

      val result2 = await(repo.createBox(Box(BoxId.random, boxName, BoxCreator(clientId))))
      result2.isInstanceOf[BoxCreateFailedResult] shouldBe true
      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 1
    }

    "create a Box should not allow creation of a duplicate box with same box details" in {
      val result: Unit = await(repo.createBox(box))
      result shouldBe ((): Unit)

      val result2 = await(repo.createBox(box))
      result2.isInstanceOf[BoxCreateFailedResult] shouldBe true

      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 1
    }
  }

  "deleteBox" should {

    "delete a Box given a valid boxId" in {
      await(repo.createBox(box))
      val fetchedRecordsOne = await(repo.collection.find().toFuture())
      fetchedRecordsOne.size shouldBe 1

      await(repo.deleteBox(box.boxId))
      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 0
    }

    "not delete a Box given a boxId that doesn't exist" in {
      await(repo.createBox(box))
      val fetchedRecordsOne = await(repo.collection.find().toFuture())
      fetchedRecordsOne.size shouldBe 1

      await(repo.deleteBox(BoxId.random))
      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 1
    }
  }

  "getBoxByBoxNameAndClientId" should {
    "should return box when it exists" in {
      val result = await(repo.getBoxByNameAndClientId(boxName, clientId))
      result shouldBe None

      await(repo.createBox(box))

      val retrievedBox = await(repo.getBoxByNameAndClientId(boxName, clientId)).get

      retrievedBox.boxCreator.clientId shouldBe clientId
      retrievedBox.boxName shouldBe boxName
    }

    "should return None when box does not exists (boxName)" in {
      await(repo.createBox(box))

      val result = await(repo.getBoxByNameAndClientId("differentBoxName", clientId))

      result shouldBe None
    }

    "should return None when box does not exist (clientId)" in {
      await(repo.createBox(box))

      val result = await(repo.getBoxByNameAndClientId(boxName, ClientId(UUID.randomUUID().toString)))

      result shouldBe None
    }
  }

  "getBoxesByClientId" should {

    val aBoxWithADifferentClientId = Box(boxName = "Another box", boxId = BoxId.random, boxCreator = BoxCreator(ClientId("Another client")))

    "return an empty list when no boxes match" in {
      await(repo.createBox(aBoxWithADifferentClientId))

      val fetchedRecords = await(repo.getBoxesByClientId(clientId))

      fetchedRecords shouldBe empty
    }

    "return only the boxes that match the clientID" in {
      val anotherBox = box.copy(BoxId.random, boxName = "someNewName")
      await(repo.createBox(box))
      await(repo.createBox(anotherBox))
      await(repo.createBox(aBoxWithADifferentClientId))

      val matchedBoxes = await(repo.getBoxesByClientId(clientId))
      val totalBoxes = await(repo.collection.find().toFuture())

      totalBoxes.size shouldBe (3)
      matchedBoxes should have length (2)
    }
  }

  "get all boxes" in {
    val anotherBox = box.copy(BoxId.random, boxName = "someNewName")

    await(repo.createBox(box))
    await(repo.createBox(anotherBox))

    val allBoxes = await(repo.getAllBoxes())

    allBoxes should have length (2)
    allBoxes shouldBe List(box, anotherBox)
  }

  "updateSubscribers" should {

    "update the subscriber list" in {
      await(repo.createBox(box))

      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 1

      val createdBox = fetchedRecords.head
      createdBox.subscriber.isDefined shouldBe false

      val updated = await(repo.updateSubscriber(createdBox.boxId, new SubscriberContainer(PushSubscriber(callBackEndpoint))))
      val updatedBox = updated.head
      updatedBox.subscriber.isDefined shouldBe true

      val subscriber = updatedBox.subscriber.get.asInstanceOf[PushSubscriber]
      subscriber.callBackUrl shouldBe callBackEndpoint
      subscriber.subscribedDateTime.isBefore(Instant.now)
    }

    "return None when the box doesn't exist" in {
      val updated = await(repo.updateSubscriber(BoxId.random, new SubscriberContainer(PushSubscriber(callBackEndpoint))))
      updated shouldBe None
    }
  }

  "updateApplicationId" should {

    "update applicationId for box if it is not present" in {
      await(repo.createBox(box))
      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 1

      val createdBox = fetchedRecords.head
      createdBox.applicationId.isDefined shouldBe false

      val appId = ApplicationId.random

      val updatedBox = await(repo.updateApplicationId(createdBox.boxId, appId))
      updatedBox.applicationId shouldBe Some(appId)
    }

    "return None when the box doesn't exist" in {
      intercept[RuntimeException] {
        await(repo.updateApplicationId(BoxId.random, ApplicationId.random))
      }
    }
  }
  "getBoxByBoxId" should {

    "return box when box exists" in {
      await(repo.createBox(box))

      val result: Option[Box] = await(repo.findByBoxId(box.boxId))

      result shouldBe Some(box)
    }

    "return empty list when box does not exist" in {
      await(repo.createBox(box))

      val result = await(repo.findByBoxId(BoxId.random))

      result shouldBe None
    }
  }

  def runWithSink(): Seq[RetryableNotification] = {
    await(
      notificationPushService.fetchRetryablePushNotifications().flatMap(_.runWith(Sink.seq))
    )
  }

  "fetchRetryablePushNotifications" should {
    val pushBox1: Box = Box(
      boxName = UUID.randomUUID().toString,
      boxId = BoxId.random,
      boxCreator = BoxCreator(ClientId(UUID.randomUUID().toString)),
      subscriber = Some(PushSubscriber("https://example.com", Instant.now.truncatedTo(ChronoUnit.MILLIS)))
    )
    val pushBox2: Box = Box(
      boxName = UUID.randomUUID().toString,
      boxId = BoxId.random,
      boxCreator = BoxCreator(ClientId(UUID.randomUUID().toString)),
      subscriber = Some(PushSubscriber("https://example.com", Instant.now.truncatedTo(ChronoUnit.MILLIS)))
    )

    "return matching notifications and boxes" in {
      val expectedNotification1 = createNotificationInDB(status = PENDING, createdDateTime = Instant.now.truncatedTo(ChronoUnit.MILLIS), boxId = pushBox1.boxId)
      val expectedNotification2 = createNotificationInDB(status = PENDING, createdDateTime = Instant.now.truncatedTo(ChronoUnit.MILLIS), boxId = pushBox1.boxId)
      val expectedNotification3 = createNotificationInDB(status = PENDING, createdDateTime = Instant.now.truncatedTo(ChronoUnit.MILLIS), boxId = pushBox2.boxId)
      val _ = createNotificationInDB(status = PENDING, createdDateTime = Instant.now.truncatedTo(ChronoUnit.MILLIS))
      await(repo.createBox(pushBox1))
      await(repo.createBox(pushBox2))

      val retryableNotifications: Seq[RetryableNotification] = runWithSink()

      retryableNotifications should have size 3
      retryableNotifications.map(_.notification) should contain.only(expectedNotification1, expectedNotification2, expectedNotification3)
      retryableNotifications.map(_.box) should contain.only(pushBox1, pushBox2)
    }

    "return matching notifications and box" in {
      val expectedNotification1 = createNotificationInDB(status = PENDING, createdDateTime = Instant.now.truncatedTo(ChronoUnit.MILLIS), boxId = pushBox1.boxId)
      val expectedNotification2 = createNotificationInDB(status = PENDING, createdDateTime = Instant.now.truncatedTo(ChronoUnit.MILLIS), boxId = pushBox1.boxId)
      val _ = createNotificationInDB(status = PENDING, createdDateTime = Instant.now.truncatedTo(ChronoUnit.MILLIS))
      await(repo.createBox(pushBox1))
      await(repo.createBox(pushBox2))

      val retryableNotifications: Seq[RetryableNotification] = runWithSink()

      retryableNotifications should have size 2
      retryableNotifications.map(_.notification) should contain.only(expectedNotification1, expectedNotification2)
      retryableNotifications.map(_.box) should contain only (pushBox1)
    }

    "not return notifications that are not pending" in {
      createNotificationInDB(status = FAILED)
      createNotificationInDB(status = ACKNOWLEDGED)
      await(repo.createBox(pushBox1))

      val retryableNotifications: Seq[RetryableNotification] = runWithSink()

      retryableNotifications should have size 0
    }

    "return pending notifications that were to be retried in the past" in {
      createNotificationInDB(status = PENDING, retryAfterDateTime = Some(Instant.now.minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MILLIS)), boxId = pushBox1.boxId)
      createNotificationInDB(status = PENDING, retryAfterDateTime = Some(Instant.now.minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS)), boxId = pushBox1.boxId)
      await(repo.createBox(pushBox1))

      val retryableNotifications: Seq[RetryableNotification] = runWithSink()

      retryableNotifications should have size 2
    }

    "not return pending notifications that are not yet to be retried" in {
      createNotificationInDB(status = PENDING, retryAfterDateTime = Some(Instant.now.plus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MILLIS)), boxId = pushBox1.boxId)
      createNotificationInDB(status = PENDING, retryAfterDateTime = Some(Instant.now.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS)), boxId = pushBox1.boxId)
      await(repo.createBox(pushBox1))

      val retryableNotifications: Seq[RetryableNotification] = runWithSink()

      retryableNotifications should have size 0
    }

    "not return pending notifications for pull subscribers" in {
      val boxForPullSubscriber: Box = Box(
        boxName = UUID.randomUUID().toString,
        boxId = boxId,
        boxCreator = BoxCreator(ClientId(UUID.randomUUID().toString)),
        subscriber = Some(PullSubscriber("https://example.com"))
      )
      createNotificationInDB(status = PENDING)
      await(repo.createBox(boxForPullSubscriber))

      val retryableNotifications: Seq[RetryableNotification] = runWithSink()

      retryableNotifications should have size 0
    }

    "not return pending notifications for which the box has no subscriber" in {
      val boxWithNoSubscriber: Box = Box(boxName = UUID.randomUUID().toString, boxId = boxId, boxCreator = BoxCreator(ClientId(UUID.randomUUID().toString)), subscriber = None)
      createNotificationInDB(status = PENDING)
      await(repo.createBox(boxWithNoSubscriber))

      val retryableNotifications: Seq[RetryableNotification] = runWithSink()
      retryableNotifications should have size 0
    }

    "not return pending notifications with emtpy callback URL" in {
      val boxWithNoCallbackUrl: Box =
        Box(boxName = UUID.randomUUID().toString, boxId = boxId, boxCreator = BoxCreator(ClientId(UUID.randomUUID().toString)), subscriber = Some(PushSubscriber("")))
      createNotificationInDB(status = PENDING)
      await(repo.createBox(boxWithNoCallbackUrl))

      val retryableNotifications: Seq[RetryableNotification] = runWithSink()

      retryableNotifications should have size 0
    }
  }

}

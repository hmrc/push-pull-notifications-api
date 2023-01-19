package uk.gov.hmrc.pushpullnotificationsapi.repository

import java.util.UUID
import akka.stream.scaladsl.Sink
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.joda.time.DateTimeZone.UTC
import org.mongodb.scala.{bson, Document}
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.MessageContentType.APPLICATION_JSON
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationId, NotificationStatus, RetryableNotification}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.{DbClient, DbNotification}
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global

class NotificationRepositoryISpec
    extends AsyncHmrcSpec
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with PlayMongoRepositorySupport[DbNotification]
    with CleanMongoCollectionSupport
    with IntegrationPatience
    with GuiceOneAppPerSuite {

  private val fourAndHalfHoursInMins = 270
  private val twoAndHalfHoursInMins = 150
  private val ttlTimeinSeconds = 3
  private val numberOfNotificationsToRetrievePerRequest = 100

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "notifications.ttlinseconds" -> ttlTimeinSeconds,
        "notifications.numberToRetrievePerRequest" -> numberOfNotificationsToRetrievePerRequest,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override implicit lazy val app: Application = appBuilder.build()
  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]

  def repo: NotificationsRepository = app.injector.instanceOf[NotificationsRepository]
  def boxRepo: BoxRepository = app.injector.instanceOf[BoxRepository]
  override protected def repository: PlayMongoRepository[DbNotification] = app.injector.instanceOf[NotificationsRepository]

  override def beforeEach() {
    prepareDatabase()
    await(repo.ensureIndexes)
  }

  def getIndex(indexName: String): Option[Document] = {
    await(repo.collection.listIndexes()
      .filter(_.getString("name").equalsIgnoreCase(indexName))
      .headOption())
  }

  private val boxIdStr = UUID.randomUUID().toString
  private val boxId = BoxId(UUID.fromString(boxIdStr))

  val box: Box = Box(
    boxName = UUID.randomUUID().toString,
    boxId = boxId,
    boxCreator = BoxCreator(ClientId(UUID.randomUUID().toString)),
    subscriber = Some(PushSubscriber("http://example.com"))
  )

  "Indexes" should {
    "create ttl index and it should have correct value " in {
      val mayBeIndex = getIndex("create_datetime_ttl_idx")
      mayBeIndex shouldNot be(None)
      val mayBeTtlValue: Long = mayBeIndex.get("expireAfterSeconds")
        .asNumber().longValue()
      mayBeTtlValue shouldBe ttlTimeinSeconds
    }
    // to get full coverage we would need to try to get the index created with an expireAfterSeconds as a BSON value that is not a long
    // not sure their is any value in this test as we know we always create as a long
  }

  "saveNotification" should {

    "test notificationId to String" in {
      val notificationIdStr = UUID.randomUUID().toString
      val notification = NotificationId(UUID.fromString(notificationIdStr))
      notification.raw shouldBe notificationIdStr
    }

    "save a Notification" in {
      val notification = Notification(NotificationId(UUID.randomUUID()), boxId, messageContentType = APPLICATION_JSON, message = "{\"someJsone\": \"someValue\"}", status = PENDING)

      val result: Unit = await(repo.saveNotification(notification))
      result shouldBe ((): Unit)
    }

    "encrypt the notification message in the database" in {
      val notification = Notification(NotificationId(UUID.randomUUID()), boxId, messageContentType = APPLICATION_JSON, message = "{\"someJsone\": \"someValue\"}", status = PENDING)

      await(repo.saveNotification(notification))

      val dbNotification: DbNotification = await(repo.collection.find()
        .toFuture()).head
      dbNotification.encryptedMessage shouldBe "7n6b74s5fsOk4jbiENErrBGgKGfrtWv8TOzHhyNvlUE="
    }

    "not save duplicate Notifications" in {
      val notification = Notification(NotificationId(UUID.randomUUID()), boxId, messageContentType = APPLICATION_JSON, message = "{", status = PENDING)
      val result: Unit = await(repo.saveNotification(notification))
      result shouldBe ((): Unit)

      await(repo.saveNotification(notification))
    }

  }

  "updateStatus" should {
    "update the matching notification with a new status" in {
      val matchingNotificationId = NotificationId(UUID.randomUUID())
      val nonMatchingNotificationId = NotificationId(UUID.randomUUID())
      createNotificationInDB(status = PENDING, notificationId = matchingNotificationId)
      createNotificationInDB(status = PENDING, notificationId = nonMatchingNotificationId)

      await(repo.updateStatus(matchingNotificationId, ACKNOWLEDGED))

      val notifications = await(repo.collection.find().toFuture())
      notifications.find(_.notificationId == matchingNotificationId).get.status shouldBe ACKNOWLEDGED
      notifications.find(_.notificationId == nonMatchingNotificationId).get.status shouldBe PENDING
    }

    "return the updated notification" in {
      val notificationId = NotificationId(UUID.randomUUID())
      createNotificationInDB(status = PENDING, notificationId = notificationId)

      val result: Notification = await(repo.updateStatus(notificationId, ACKNOWLEDGED))
      result.status shouldBe ACKNOWLEDGED
    }
  }

  "updateRetryAfterDateTime" should {
    "update the matching notification with a new retry after date time" in {
      val matchingNotificationId = NotificationId(UUID.randomUUID())
      val nonMatchingNotificationId = NotificationId(UUID.randomUUID())
      createNotificationInDB(status = PENDING, notificationId = matchingNotificationId)
      createNotificationInDB(status = PENDING, notificationId = nonMatchingNotificationId)
      val newDateTime = now(UTC).plusHours(2)

      await(repo.updateRetryAfterDateTime(matchingNotificationId, newDateTime))

      val notifications = await(repo.collection.find().toFuture())
      notifications.find(_.notificationId == matchingNotificationId).get.retryAfterDateTime shouldBe Some(newDateTime)
      notifications.find(_.notificationId == nonMatchingNotificationId).get.retryAfterDateTime shouldBe None
    }

    "return the updated notification" in {
      val notificationId = NotificationId(UUID.randomUUID())
      createNotificationInDB(status = PENDING, notificationId = notificationId)

      val result: Notification = await(repo.updateStatus(notificationId, ACKNOWLEDGED))
      result.status shouldBe ACKNOWLEDGED
    }
  }

  "fetchRetryableNotifications" should {
    "return matching notifications and box" in {
      val expectedNotification1 = createNotificationInDB(status = PENDING)
      val expectedNotification2 = createNotificationInDB(status = PENDING)
      await(boxRepo.createBox(box))

      val retryableNotifications: Seq[RetryableNotification] = await(repo.fetchRetryableNotifications.runWith(Sink.seq))

      retryableNotifications should have size 2
      retryableNotifications.map(_.notification) should contain only (expectedNotification1, expectedNotification2)
      retryableNotifications.map(_.box) should contain only box
    }

    "not return notifications that are not pending" in {
      createNotificationInDB(status = FAILED)
      createNotificationInDB(status = ACKNOWLEDGED)
      await(boxRepo.createBox(box))

      val retryableNotifications: Seq[RetryableNotification] = await(repo.fetchRetryableNotifications.runWith(Sink.seq))

      retryableNotifications should have size 0
    }

    "return pending notifications that were to be retried in the past" in {
      createNotificationInDB(status = PENDING, retryAfterDateTime = Some(now(UTC).minusHours(2)))
      createNotificationInDB(status = PENDING, retryAfterDateTime = Some(now(UTC).minusDays(1)))
      await(boxRepo.createBox(box))

      val retryableNotifications: Seq[RetryableNotification] = await(repo.fetchRetryableNotifications.runWith(Sink.seq))

      retryableNotifications should have size 2
    }

    "not return pending notifications that are not yet to be retried" in {
      createNotificationInDB(status = PENDING, retryAfterDateTime = Some(now(UTC).plusHours(2)))
      createNotificationInDB(status = PENDING, retryAfterDateTime = Some(now(UTC).plusDays(1)))
      await(boxRepo.createBox(box))

      val retryableNotifications: Seq[RetryableNotification] = await(repo.fetchRetryableNotifications.runWith(Sink.seq))

      retryableNotifications should have size 0
    }

    "not return pending notifications for pull subscribers" in {
      val boxForPullSubscriber: Box = Box(
        boxName = UUID.randomUUID().toString,
        boxId = boxId,
        boxCreator = BoxCreator(ClientId(UUID.randomUUID().toString)),
        subscriber = Some(PullSubscriber("http://example.com"))
      )
      createNotificationInDB(status = PENDING)
      await(boxRepo.createBox(boxForPullSubscriber))

      val retryableNotifications: Seq[RetryableNotification] = await(repo.fetchRetryableNotifications.runWith(Sink.seq))

      retryableNotifications should have size 0
    }

    "not return pending notifications for which the box has no subscriber" in {
      val boxWithNoSubscriber: Box = Box(boxName = UUID.randomUUID().toString, boxId = boxId, boxCreator = BoxCreator(ClientId(UUID.randomUUID().toString)), subscriber = None)
      createNotificationInDB(status = PENDING)
      await(boxRepo.createBox(boxWithNoSubscriber))

      val retryableNotifications: Seq[RetryableNotification] = await(repo.fetchRetryableNotifications.runWith(Sink.seq))

      retryableNotifications should have size 0
    }

    "not return pending notifications with emtpy callback URL" in {
      val boxWithNoCallbackUrl: Box =
        Box(boxName = UUID.randomUUID().toString, boxId = boxId, boxCreator = BoxCreator(ClientId(UUID.randomUUID().toString)), subscriber = Some(PushSubscriber("")))
      createNotificationInDB(status = PENDING)
      await(boxRepo.createBox(boxWithNoCallbackUrl))

      val retryableNotifications: Seq[RetryableNotification] = await(repo.fetchRetryableNotifications.runWith(Sink.seq))

      retryableNotifications should have size 0
    }
  }

  "getByBoxIdAndFilters" should {

    "return list of notifications for a box id when no other filters are present" in {
      await(repo.getByBoxIdAndFilters(boxId)).isEmpty shouldBe true

      createNotificationInDB()
      createNotificationInDB()

      val notifications: List[Notification] = await(repo.getByBoxIdAndFilters(boxId))
      notifications.isEmpty shouldBe false
      notifications.size shouldBe 2
    }

    "return empty list for a non existent / unknown box id" in {
      await(repo.getAllByBoxId(BoxId(UUID.randomUUID()))).isEmpty shouldBe true
    }

    "return list of notification for boxId filtered by status" in {
      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe true

      createNotificationInDB()
      createNotificationInDB()
      createNotificationInDB(ACKNOWLEDGED)

      val notifications: List[Notification] = await(repo.getAllByBoxId(boxId))
      notifications.isEmpty shouldBe false
      notifications.size shouldBe 3

      val filteredList = await(repo.getByBoxIdAndFilters(boxId, Some(PENDING)))
      filteredList.isEmpty shouldBe false
      filteredList.size shouldBe 2

      val filteredList2 = await(repo.getByBoxIdAndFilters(boxId, Some(ACKNOWLEDGED)))
      filteredList2.isEmpty shouldBe false
      filteredList2.size shouldBe 1
    }

    "return empty List when notifications exist but do not match the given status filter" in {
      createNotificationInDB()

      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe false
      await(repo.getByBoxIdAndFilters(boxId, Some(ACKNOWLEDGED))).isEmpty shouldBe true
    }

    "return empty List when no notification exist for boxId" in {
      createNotificationInDB()

      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe false
      await(repo.getByBoxIdAndFilters(BoxId(UUID.randomUUID()), Some(PENDING))).isEmpty shouldBe true
    }

    "return list of notification for boxId gte fromDate and lt toDate" in {
      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe true
      val notificationsToCreate = 7
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)

      val filteredList =
        await(repo.getByBoxIdAndFilters(boxId, Some(PENDING), fromDateTime = Some(DateTime.now().minusMinutes(twoAndHalfHoursInMins)), toDateTime = Some(DateTime.now())))
      filteredList.size shouldBe 3
    }

    "return list of notification for boxId gte fromDate when to date is missing" in {
      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe true

      val notificationsToCreate = 9
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)

      val filteredList = await(repo.getByBoxIdAndFilters(boxId, Some(PENDING), fromDateTime = Some(DateTime.now().minusMinutes(fourAndHalfHoursInMins))))
      filteredList.size shouldBe 5
    }

    "return list of notification for boxId lt toDate when from date is missing" in {
      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe true

      val notificationsToCreate = 11
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)

      val filteredList = await(repo.getByBoxIdAndFilters(boxId, Some(PENDING), toDateTime = Some(DateTime.now().minusMinutes(fourAndHalfHoursInMins))))
      filteredList.size shouldBe 6
    }

    "return list of notification for boxId gte fromDate and lt toDate without status filter" in {
      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe true
      val notificationsToCreate = 7
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)
      createNotificationInDB(createdDateTime = DateTime.now().minusMinutes(twoAndHalfHoursInMins - 30), status = ACKNOWLEDGED)
      createNotificationInDB(createdDateTime = DateTime.now().minusMinutes(twoAndHalfHoursInMins - 30), status = ACKNOWLEDGED)

      val filteredList = await(repo.getByBoxIdAndFilters(boxId, fromDateTime = Some(DateTime.now().minusMinutes(twoAndHalfHoursInMins)), toDateTime = Some(DateTime.now())))
      filteredList.count(n => n.status == ACKNOWLEDGED) shouldBe 2
      filteredList.count(n => n.status == PENDING) shouldBe 3
    }

    "limit number of results, returning oldest first" in {
      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe true

      // Create enough notifications to be returned in one request
      for (_ <- 0 until numberOfNotificationsToRetrievePerRequest) {
        createNotificationInDB(createdDateTime = DateTime.now().minusDays(1))
      }

      // And 1 for today, which should not be returned
      val mostRecentDate = DateTime.now
      createNotificationInDB(createdDateTime = mostRecentDate)

      val returnedNotifications = await(repo.getByBoxIdAndFilters(boxId))

      returnedNotifications.size should be(numberOfNotificationsToRetrievePerRequest)
      returnedNotifications.filter(n => n.createdDateTime.isEqual(mostRecentDate)) should be(List.empty)
    }
  }

  "AcknowledgeNotifications" should {
    "update the statuses of ALL created notifications to ACKNOWLEDGED" in {
      val notificationIdsDoNotUpdate = List(NotificationId(UUID.randomUUID()), NotificationId(UUID.randomUUID()), NotificationId(UUID.randomUUID()))
      val notificationIdsToUpdate = List(NotificationId(UUID.randomUUID()), NotificationId(UUID.randomUUID()), NotificationId(UUID.randomUUID()))
      createNotificationsWithIds(notificationIdsDoNotUpdate)
      createNotificationsWithIds(notificationIdsToUpdate)

      val returnedNotificationsBeforeUpdate = await(repo.getByBoxIdAndFilters(boxId))
      returnedNotificationsBeforeUpdate.count(_.status.equals(PENDING)) shouldBe 6
      returnedNotificationsBeforeUpdate.count(_.status.equals(ACKNOWLEDGED)) shouldBe 0
      val result = await(repo.acknowledgeNotifications(boxId, notificationIdsToUpdate.map(_.value.toString)))
      result shouldBe true

      val returnedNotificationsAfterUpdate = await(repo.getByBoxIdAndFilters(boxId))
      returnedNotificationsAfterUpdate.count(_.status.equals(PENDING)) shouldBe 3
      returnedNotificationsAfterUpdate.count(_.status.equals(ACKNOWLEDGED)) shouldBe 3
    }

  }

  private def validateNotificationsCreated(numberExpected: Int): Unit = {
    val notifications: List[Notification] = await(repo.getAllByBoxId(boxId))
    notifications.isEmpty shouldBe false
    notifications.size shouldBe numberExpected
  }

  private def createNotificationInDB(
      status: NotificationStatus = PENDING,
      createdDateTime: DateTime = now(UTC),
      notificationId: NotificationId = NotificationId(UUID.randomUUID()),
      retryAfterDateTime: Option[DateTime] = None
    ): Notification = {
    val notification = Notification(
      notificationId,
      boxId = boxId,
      APPLICATION_JSON,
      message = "{\"someJsone\": \"someValue\"}",
      status = status,
      createdDateTime = createdDateTime,
      retryAfterDateTime = retryAfterDateTime
    )

    val result: Unit = await(repo.saveNotification(notification))
    result shouldBe ((): Unit)
    notification
  }

  private def createHistoricalNotifications(numberToCreate: Int): Unit = {
    for (a <- 0 until numberToCreate) {
      createNotificationInDB(createdDateTime = DateTime.now().minusHours(a))
    }
  }

  private def createNotificationsWithIds(notificationIds: List[NotificationId]): Unit = {
    for (a <- notificationIds.indices) {
      createNotificationInDB(createdDateTime = DateTime.now().minusHours(a), notificationId = notificationIds(a))
    }
  }

}

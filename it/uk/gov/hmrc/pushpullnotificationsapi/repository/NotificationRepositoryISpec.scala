package uk.gov.hmrc.pushpullnotificationsapi.repository

import java.util.UUID

import org.joda.time.DateTime
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import reactivemongo.api.indexes.Index
import reactivemongo.bson.BSONLong
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.MessageContentType.APPLICATION_JSON
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.support.MongoApp

import scala.concurrent.ExecutionContext.Implicits.global

class NotificationRepositoryISpec extends UnitSpec with MongoApp with GuiceOneAppPerSuite {

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

  def repo: NotificationsRepository =
    app.injector.instanceOf[NotificationsRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
    await(repo.ensureIndexes)
  }

  def getIndex(indexName: String): Option[Index] ={
    await(repo.collection.indexesManager.list().map(_.find(_.eventualName.equalsIgnoreCase(indexName))))
  }

  "Indexes" should {
    "create ttl index and it should have correct value "in {
      val mayBeIndex = getIndex("create_datetime_ttl_idx")
      mayBeIndex shouldNot be(None)
      val mayBeTtlValue: Option[Long] = mayBeIndex.flatMap(_.options.getAs[BSONLong]("expireAfterSeconds").map(_.as[Long]))
      mayBeTtlValue  shouldNot be(None)
      mayBeTtlValue.head shouldBe ttlTimeinSeconds
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
      val notification = Notification(NotificationId(UUID.randomUUID()), boxId,
        messageContentType = APPLICATION_JSON,
        message = "{\"someJsone\": \"someValue\"}",
        status = PENDING)
      val result: Unit = await(repo.saveNotification(notification))
      result shouldBe ((): Unit)


    }

    "not save duplicate Notifications" in {
      val notification = Notification(NotificationId(UUID.randomUUID()), boxId,
        messageContentType = APPLICATION_JSON,
        message = "{",
        status = PENDING)


      val result: Unit = await(repo.saveNotification(notification))
      result shouldBe ((): Unit)


      await(repo.saveNotification(notification))

    }

  }
  private val boxIdStr = UUID.randomUUID().toString
  private val boxId = BoxId(UUID.fromString(boxIdStr))

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

      val filteredList = await(repo.getByBoxIdAndFilters(boxId,
        Some(PENDING),
        fromDateTime = Some(DateTime.now().minusMinutes(twoAndHalfHoursInMins)),
        toDateTime = Some(DateTime.now())))
      filteredList.size shouldBe 3
    }

    "return list of notification for boxId gte fromDate when to date is missing" in {
      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe true

      val notificationsToCreate = 9
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)

      val filteredList = await(repo.getByBoxIdAndFilters(boxId,
        Some(PENDING),
        fromDateTime = Some(DateTime.now().minusMinutes(fourAndHalfHoursInMins))
      ))
      filteredList.size shouldBe 5
    }


    "return list of notification for boxId lt toDate when from date is missing" in {
      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe true

      val notificationsToCreate = 11
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)

      val filteredList = await(repo.getByBoxIdAndFilters(boxId,
        Some(PENDING),
        toDateTime = Some(DateTime.now().minusMinutes(fourAndHalfHoursInMins))
      ))
      filteredList.size shouldBe 6
    }

    "return list of notification for boxId gte fromDate and lt toDate without status filter" in {
      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe true
      val notificationsToCreate = 7
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)
      createNotificationInDB(createdDateTime = DateTime.now().minusMinutes(twoAndHalfHoursInMins - 30), status = ACKNOWLEDGED)
      createNotificationInDB(createdDateTime = DateTime.now().minusMinutes(twoAndHalfHoursInMins - 30), status = ACKNOWLEDGED)

      val filteredList = await(repo.getByBoxIdAndFilters(boxId,
        fromDateTime = Some(DateTime.now().minusMinutes(twoAndHalfHoursInMins)),
        toDateTime = Some(DateTime.now())))
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

      returnedNotifications.size should be (numberOfNotificationsToRetrievePerRequest)
      returnedNotifications.filter(n => n.createdDateTime.isEqual(mostRecentDate)) should be (List.empty)
    }
  }

  private def validateNotificationsCreated(numberExpected: Int): Unit = {
    val notifications: List[Notification] = await(repo.getAllByBoxId(boxId))
    notifications.isEmpty shouldBe false
    notifications.size shouldBe numberExpected
  }

  private def createNotificationInDB(status: NotificationStatus = PENDING, createdDateTime: DateTime = DateTime.now()) = {
    val notification = Notification(NotificationId(UUID.randomUUID()),
      boxId = boxId,
      APPLICATION_JSON,
      message = "{\"someJsone\": \"someValue\"}",
      status = status,
      createdDateTime = createdDateTime)

    val result: Unit = await(repo.saveNotification(notification))
    result shouldBe ((): Unit)
  }

  private def createHistoricalNotifications(numberToCreate: Int): Unit = {
    for (a <- 0 until numberToCreate) {
      createNotificationInDB(createdDateTime = DateTime.now().minusHours(a))
    }
  }

}

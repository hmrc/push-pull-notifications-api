package uk.gov.hmrc.pushpullnotificationsapi.repository

import java.util.concurrent.TimeUnit
import java.util.{Timer, TimerTask, UUID}

import org.joda.time.DateTime
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.concurrent.Akka
import reactivemongo.api.indexes.Index
import reactivemongo.bson.BSONLong
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.MessageContentType.APPLICATION_JSON
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.support.{Awaiting, MongoApp}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}

class NotificationRepositoryISpec extends UnitSpec with MongoApp with GuiceOneAppPerSuite {

  private val fourAndHalfHoursInMins = 270
  private val twoAndHalfHoursInMins = 150
  private val ttlTimeinSeconds = 3

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
              "notifications.ttlinseconds" -> ttlTimeinSeconds,
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
        status = RECEIVED)
      val result: Unit = await(repo.saveNotification(notification))
      result shouldBe ((): Unit)


    }

    "not save duplicate Notifications" in {
      val notification = Notification(NotificationId(UUID.randomUUID()), boxId,
        messageContentType = APPLICATION_JSON,
        message = "{",
        status = RECEIVED)


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
      createNotificationInDB(READ)

      val notifications: List[Notification] = await(repo.getAllByBoxId(boxId))

      notifications.isEmpty shouldBe false
      notifications.size shouldBe 3

      val filteredList = await(repo.getByBoxIdAndFilters(boxId, Some(RECEIVED)))
      filteredList.isEmpty shouldBe false
      filteredList.size shouldBe 2
    }


    "return empty List when notifications exist but do not match the given status filter" in {
      createNotificationInDB()

      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe false
      await(repo.getByBoxIdAndFilters(boxId, Some(READ))).isEmpty shouldBe true
    }

    "return empty List when no notification exist for boxId" in {
      createNotificationInDB()

      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe false
      await(repo.getByBoxIdAndFilters(BoxId(UUID.randomUUID()), Some(RECEIVED))).isEmpty shouldBe true
    }


    "return list of notification for boxId gte fromDate and lt toDate" in {
      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe true
      val notificationsToCreate = 7
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)

      val filteredList = await(repo.getByBoxIdAndFilters(boxId,
        Some(RECEIVED),
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
        Some(RECEIVED),
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
        Some(RECEIVED),
        toDateTime = Some(DateTime.now().minusMinutes(fourAndHalfHoursInMins))
      ))
      filteredList.size shouldBe 6
    }

    "return list of notification for boxId gte fromDate and lt toDate without status filter" in {
      await(repo.getAllByBoxId(boxId)).isEmpty shouldBe true
      val notificationsToCreate = 7
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)
      createNotificationInDB(createdDateTime = DateTime.now().minusMinutes(twoAndHalfHoursInMins - 30), status = READ)
      createNotificationInDB(createdDateTime = DateTime.now().minusMinutes(twoAndHalfHoursInMins - 30), status = READ)

      val filteredList = await(repo.getByBoxIdAndFilters(boxId,
        fromDateTime = Some(DateTime.now().minusMinutes(twoAndHalfHoursInMins)),
        toDateTime = Some(DateTime.now())))
      filteredList.count(n => n.status == READ) shouldBe 2
      filteredList.count(n => n.status == RECEIVED) shouldBe 3
    }
  }

  private def validateNotificationsCreated(numberExpected: Int): Unit = {
    val notifications: List[Notification] = await(repo.getAllByBoxId(boxId))
    notifications.isEmpty shouldBe false
    notifications.size shouldBe numberExpected
  }

  private def createNotificationInDB(status: NotificationStatus = RECEIVED, createdDateTime: DateTime = DateTime.now()) = {
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

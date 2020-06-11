package uk.gov.hmrc.pushpullnotificationsapi.repository

import java.util.UUID

import org.joda.time.DateTime
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
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

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
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


  "saveNotification" should {

    "test notificationId to String" in {
      val notificationIdStr = UUID.randomUUID().toString
      val notification = NotificationId(UUID.fromString(notificationIdStr))
      notification.raw shouldBe notificationIdStr
    }

    "save a Notification" in {
      val notification = Notification(NotificationId(UUID.randomUUID()), topicId,
        messageContentType = APPLICATION_JSON,
        message = "{\"someJsone\": \"someValue\"}",
        status = RECEIVED)
      val result: Unit = await(repo.saveNotification(notification))
      result shouldBe ((): Unit)


    }

    "not save duplicate Notifications" in {
      val notification = Notification(NotificationId(UUID.randomUUID()), topicId = topicId,
        messageContentType = APPLICATION_JSON,
        message = "{",
        status = RECEIVED)


      val result: Unit = await(repo.saveNotification(notification))
      result shouldBe ((): Unit)


      await(repo.saveNotification(notification))

    }

  }
  private val topicIdStr = UUID.randomUUID().toString
  private val topicId = TopicId(UUID.fromString(topicIdStr))

  "getByTopicIdAndFilters" should {

    "return list of notifications for a topic id when no other filters are present" in {
      await(repo.getByTopicIdAndFilters(topicId)).isEmpty shouldBe true

      createNotificationInDB()
      createNotificationInDB()

      val notifications: List[Notification] = await(repo.getByTopicIdAndFilters(topicId))

      notifications.isEmpty shouldBe false
      notifications.size shouldBe 2
    }

    "return empty list for a non existent / unknown topic id" in {
      await(repo.getAllByTopicId(TopicId(UUID.randomUUID()))).isEmpty shouldBe true
    }

    "return list of notification for topicId filtered by status" in {
      await(repo.getAllByTopicId(topicId)).isEmpty shouldBe true

      createNotificationInDB()
      createNotificationInDB()
      createNotificationInDB(READ)

      val notifications: List[Notification] = await(repo.getAllByTopicId(topicId))

      notifications.isEmpty shouldBe false
      notifications.size shouldBe 3

      val filteredList = await(repo.getByTopicIdAndFilters(topicId, Some(RECEIVED)))
      filteredList.isEmpty shouldBe false
      filteredList.size shouldBe 2
    }


    "return empty List when notifications exist but do not match the given status filter" in {
      createNotificationInDB()

      await(repo.getAllByTopicId(topicId)).isEmpty shouldBe false
      await(repo.getByTopicIdAndFilters(topicId, Some(READ))).isEmpty shouldBe true
    }

    "return empty List when no notification exist for topicId" in {
      createNotificationInDB()

      await(repo.getAllByTopicId(topicId)).isEmpty shouldBe false
      await(repo.getByTopicIdAndFilters(TopicId(UUID.randomUUID()), Some(RECEIVED))).isEmpty shouldBe true
    }


    "return list of notification for topicId gte fromDate and lt toDate" in {
      await(repo.getAllByTopicId(topicId)).isEmpty shouldBe true
      val notificationsToCreate = 7
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)

      val filteredList = await(repo.getByTopicIdAndFilters(topicId,
        Some(RECEIVED),
        fromDateTime = Some(DateTime.now().minusMinutes(twoAndHalfHoursInMins)),
        toDateTime = Some(DateTime.now())))
      filteredList.size shouldBe 3
    }

    "return list of notification for topicId gte fromDate when to date is missing" in {
      await(repo.getAllByTopicId(topicId)).isEmpty shouldBe true

      val notificationsToCreate = 9
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)

      val filteredList = await(repo.getByTopicIdAndFilters(topicId,
        Some(RECEIVED),
        fromDateTime = Some(DateTime.now().minusMinutes(fourAndHalfHoursInMins))
      ))
      filteredList.size shouldBe 5
    }


    "return list of notification for topicId lt toDate when from date is missing" in {
      await(repo.getAllByTopicId(topicId)).isEmpty shouldBe true

      val notificationsToCreate = 11
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)

      val filteredList = await(repo.getByTopicIdAndFilters(topicId,
        Some(RECEIVED),
        toDateTime = Some(DateTime.now().minusMinutes(fourAndHalfHoursInMins))
      ))
      filteredList.size shouldBe 6
    }

    "return list of notification for topicId gte fromDate and lt toDate without status filter" in {
      await(repo.getAllByTopicId(topicId)).isEmpty shouldBe true
      val notificationsToCreate = 7
      createHistoricalNotifications(notificationsToCreate)
      validateNotificationsCreated(notificationsToCreate)
      createNotificationInDB(createdDateTime = DateTime.now().minusMinutes(twoAndHalfHoursInMins - 30), status = READ)
      createNotificationInDB(createdDateTime = DateTime.now().minusMinutes(twoAndHalfHoursInMins - 30), status = READ)

      val filteredList = await(repo.getByTopicIdAndFilters(topicId,
        fromDateTime = Some(DateTime.now().minusMinutes(twoAndHalfHoursInMins)),
        toDateTime = Some(DateTime.now())))
      filteredList.count(n => n.status == READ) shouldBe 2
      filteredList.count(n => n.status == RECEIVED) shouldBe 3
    }
  }

  private def validateNotificationsCreated(numberExpected: Int): Unit = {
    val notifications: List[Notification] = await(repo.getAllByTopicId(topicId))
    notifications.isEmpty shouldBe false
    notifications.size shouldBe numberExpected
  }

  private def createNotificationInDB(status: NotificationStatus = RECEIVED, createdDateTime: DateTime = DateTime.now()) = {
    val notification = Notification(NotificationId(UUID.randomUUID()),
      topicId = topicId,
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

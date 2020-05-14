package uk.gov.hmrc.pushpullnotificationsapi.repository

import java.util.UUID

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{Notification, NotificationContentType, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.support.MongoApp

import scala.concurrent.ExecutionContext.Implicits.global

class NotificationRepositoryISpec extends UnitSpec with MongoApp with GuiceOneAppPerSuite {

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
   // dropMongoDb()
    await(repo.ensureIndexes)
  }


  "saveNotification" should {

    "save a Notification" in {
      val notification = Notification(UUID.randomUUID(), topicId = "someTopicId",
        notificationContentType = NotificationContentType.APPLICATION_JSON,
        message = "{\"someJsone\": \"someValue\"}",
        status = NotificationStatus.RECEIVED)
      val result: Unit = await(repo.saveNotification(notification))
      result shouldBe (())


    }

    "not save duplicate Notifications" in {
      val notification = Notification(UUID.randomUUID(), topicId = "someTopicId",
        notificationContentType = NotificationContentType.APPLICATION_JSON,
        message = "{",
        status = NotificationStatus.RECEIVED)


      val result: Unit = await(repo.saveNotification(notification))
      result shouldBe (())

      intercept[DuplicateNotificationException] {
        await(repo.saveNotification(notification))
      }
    }

  }

}

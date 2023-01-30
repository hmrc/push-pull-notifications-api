package uk.gov.hmrc.pushpullnotificationsapi.repository

import org.mongodb.scala.model.Filters.{equal => mongoEqual}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.IntegrationPatience
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.ConfirmationRequest
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.ConfirmationId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.{ACKNOWLEDGED, FAILED, PENDING}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.PlayHmrcMongoFormatters._

import java.time.temporal.ChronoUnit
import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class ConfirmationRepositoryISpec
    extends AsyncHmrcSpec
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with PlayMongoRepositorySupport[ConfirmationRequest]
    with CleanMongoCollectionSupport
    with IntegrationPatience
    with GuiceOneAppPerSuite {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  val confirmationId: ConfirmationId = ConfirmationId(UUID.randomUUID())
  val notificationId: NotificationId = NotificationId(UUID.randomUUID())

  val defaultRequest: ConfirmationRequest = ConfirmationRequest(
    confirmationId,
    "http://testurl.com/",
    notificationId,
    PENDING,
    Instant.now.truncatedTo(ChronoUnit.MILLIS)
  )
  override implicit lazy val app: Application = appBuilder.build()

  override def beforeEach() {
    prepareDatabase()
  }

  override protected def repository: PlayMongoRepository[ConfirmationRequest] = app.injector.instanceOf[ConfirmationRepository]

  def repo: ConfirmationRepository = repository.asInstanceOf[ConfirmationRepository]

  "saveConfirmationRequest" should {
    "Save a confirmation request" in {
      await(repo.saveConfirmationRequest(defaultRequest))
      val result = await(find(mongoEqual("confirmationId", Codecs.toBson(confirmationId))))
      result.length shouldBe 1
      result.head shouldBe defaultRequest
    }

    "only save 1 confirmation request per notification" in {
      await(repo.saveConfirmationRequest(defaultRequest))
      val dupe = await(repo.saveConfirmationRequest(defaultRequest.copy(confirmationId = ConfirmationId(UUID.randomUUID()))))
      dupe shouldBe None
      val result = await(find(mongoEqual("notificationId", Codecs.toBson(notificationId))))
      result.length shouldBe 1
      result.head shouldBe defaultRequest
    }
  }
  "updateConfirmationNeed" should {
    "update pushedDateTime" in {
      await(repo.saveConfirmationRequest(defaultRequest))
      val first = await(find(mongoEqual("confirmationId", Codecs.toBson(confirmationId))))
      first.head.pushedDateTime shouldBe None
      val updated = await(repo.updateConfirmationNeed(notificationId))
      updated.get.pushedDateTime.isDefined shouldBe true
    }

    "should return None if no notification to update" in {
      val updated = await(repo.updateConfirmationNeed(notificationId))
      updated shouldBe None
    }
  }
  "updateConfirmationStatus" should {
    "update to match status passed in" in {
      await(repo.saveConfirmationRequest(defaultRequest))
      val first = await(find(mongoEqual("confirmationId", Codecs.toBson(confirmationId))))
      first.head.status shouldBe PENDING
      val updated = await(repo.updateConfirmationStatus(notificationId, ACKNOWLEDGED))
      updated.get.status shouldBe ACKNOWLEDGED
      val updated2 = await(repo.updateConfirmationStatus(notificationId, FAILED))
      updated2.get.status shouldBe FAILED
    }

    "should return None if no notification to update" in {
      val updated = await(repo.updateConfirmationStatus(notificationId, ACKNOWLEDGED))
      updated shouldBe None
    }
  }
}

package uk.gov.hmrc.pushpullnotificationsapi.repository

import akka.stream.scaladsl.Sink
import org.mongodb.scala.model.Filters.{equal => mongoEqual}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.IntegrationPatience
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.Play.materializer
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.ConfirmationRequest
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.ConfirmationId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.{NotificationId, NotificationStatus}
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus.{ACKNOWLEDGED, FAILED, PENDING}
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.PlayHmrcMongoFormatters._

import java.time.{Duration, Instant}
import java.time.temporal.ChronoUnit
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.ConfirmationStatus
import java.net.URL
import com.mongodb.client.result.InsertOneResult

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
        "mongodb.uri" -> mongoUri
      )

  val url = new URL("http://testurl.com/")
  val confirmationId: ConfirmationId = ConfirmationId.random
  val notificationId: NotificationId = NotificationId.random

  val defaultRequest: ConfirmationRequest = ConfirmationRequest(
    confirmationId,
    url,
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

  // def saveMongoJsonWithBadUrl(input: ConfirmationRequest): InsertOneResult = {
  //   val rawJson = (
      
  //   )
  //   await(mongoDatabase.getCollection("user").insertOne(Document(userAsRawJson.toString())).toFuture())
  // }
  //
  // "handle a bad URL accordingly" should {
  //   "go bang" in {

  //   }
  // }
  "saveConfirmationRequest" should {
    "Save a confirmation request" in {
      await(repo.saveConfirmationRequest(defaultRequest))
      val result = await(find(mongoEqual("confirmationId", Codecs.toBson(confirmationId))))
      result.length shouldBe 1
      result.head shouldBe defaultRequest
    }

    "only save 1 confirmation request per notification" in {
      await(repo.saveConfirmationRequest(defaultRequest))
      val dupe = await(repo.saveConfirmationRequest(defaultRequest.copy(confirmationId = ConfirmationId.random)))
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
      val updated = await(repo.updateStatus(notificationId, ConfirmationStatus.ACKNOWLEDGED))
      updated.get.status shouldBe ACKNOWLEDGED
      val updated2 = await(repo.updateStatus(notificationId, ConfirmationStatus.FAILED))
      updated2.get.status shouldBe FAILED
    }

    "should return None if no notification to update" in {
      val updated = await(repo.updateStatus(notificationId, ConfirmationStatus.ACKNOWLEDGED))
      updated shouldBe None
    }
  }

  "updateRetryAfterDateTime" should {
    "update to match time passed in" in {
      val inABit = Instant.now.truncatedTo(ChronoUnit.MILLIS).plus(Duration.ofMinutes(5))
      await(repo.saveConfirmationRequest(defaultRequest))
      val first = await(find(mongoEqual("confirmationId", Codecs.toBson(confirmationId))))
      first.head.retryAfterDateTime shouldBe None
      val updated = await(repo.updateRetryAfterDateTime(notificationId, inABit))
      updated.get.retryAfterDateTime shouldBe Some(inABit)
    }

    "should return None if no notification to update" in {
      val updated = await(repo.updateRetryAfterDateTime(notificationId, Instant.now))
      updated shouldBe None
    }
  }

  "fetchRetryableConfirmations" should {

    def createConfirmationInDb(status: NotificationStatus, retryAfterDateTime: Option[Instant] = None) = {
      val id = ConfirmationId.random
      val confirmation = ConfirmationRequest(id, url, NotificationId.random, status, pushedDateTime = Some(Instant.now), retryAfterDateTime = retryAfterDateTime)
      val result = await(repo.saveConfirmationRequest(confirmation))
      result shouldBe Some(id)
      confirmation
    }

    "return matching confirmations" in {
      val expectedNotification1 = createConfirmationInDb(status = PENDING)
      val expectedNotification2 = createConfirmationInDb(status = PENDING)
      val retryableConfirmations: Seq[ConfirmationRequest] = await(repo.fetchRetryableConfirmations.runWith(Sink.seq))

      retryableConfirmations should have size 2
      retryableConfirmations.map(_.notificationId) should contain only (expectedNotification1.notificationId, expectedNotification2.notificationId)
    }

    "not return confirmations that are not pending" in {
      createConfirmationInDb(status = FAILED)
      createConfirmationInDb(status = ACKNOWLEDGED)

      val retryableConfirmations: Seq[ConfirmationRequest] = await(repo.fetchRetryableConfirmations.runWith(Sink.seq))

      retryableConfirmations should have size 0
    }

    "return pending confirmations that were to be retried in the past" in {
      createConfirmationInDb(status = PENDING, retryAfterDateTime = Some(Instant.now.minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MILLIS)))
      createConfirmationInDb(status = PENDING, retryAfterDateTime = Some(Instant.now.minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS)))
      val retryableConfirmations: Seq[ConfirmationRequest] = await(repo.fetchRetryableConfirmations.runWith(Sink.seq))

      retryableConfirmations should have size 2
    }

    "not return pending confirmations that are not yet to be retried" in {
      createConfirmationInDb(status = PENDING, retryAfterDateTime = Some(Instant.now.plus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MILLIS)))
      createConfirmationInDb(status = PENDING, retryAfterDateTime = Some(Instant.now.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS)))

      val retryableConfirmations: Seq[ConfirmationRequest] = await(repo.fetchRetryableConfirmations.runWith(Sink.seq))

      retryableConfirmations should have size 0
    }
  }

}

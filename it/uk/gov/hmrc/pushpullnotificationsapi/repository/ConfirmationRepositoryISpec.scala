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
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.{ConfirmationRequest, ConfirmationRequestDB}
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.ConfirmationId
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationId
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.PlayHmrcMongoFormatters._

import java.time.{Duration, Instant}
import java.time.temporal.ChronoUnit
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.ConfirmationStatus
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.ConfirmationStatus._

import java.net.URL
import com.mongodb.client.result.InsertOneResult
import org.mongodb.scala.bson.collection.immutable.Document

class ConfirmationRepositoryISpec
    extends AsyncHmrcSpec
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with PlayMongoRepositorySupport[ConfirmationRequestDB]
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
    List.empty,
    ConfirmationStatus.PENDING,
    Instant.now.truncatedTo(ChronoUnit.MILLIS)
  )
  override implicit lazy val app: Application = appBuilder.build()

  override def beforeEach(): Unit = {
    prepareDatabase()
  }

  override protected def repository: PlayMongoRepository[ConfirmationRequestDB] = app.injector.instanceOf[ConfirmationRepository]

  def repo: ConfirmationRepository = repository.asInstanceOf[ConfirmationRepository]

  def saveMongoJsonWithBadUrl(input: ConfirmationRequest): InsertOneResult = {
    import play.api.libs.json._

    val rawJson = Json.toJson(input.toDB).as[JsObject]
    val editedJson: JsObject = rawJson + ("confirmationUrl" -> JsString("BOB"))

    await(mongoDatabase.getCollection("confirmations").insertOne(Document(editedJson.toString())).toFuture())
  }

  "handle a bad URL accordingly" should {
    "don't break when reading bad URLS with raw mongo driver" in {
      saveMongoJsonWithBadUrl(defaultRequest)
      await(find(mongoEqual("confirmationId", Codecs.toBson(confirmationId))))
    }
  }

  "saveConfirmationRequest" should {
    "Save a confirmation request" in {
      await(repo.saveConfirmationRequest(defaultRequest))
      val result = await(find(mongoEqual("confirmationId", Codecs.toBson(confirmationId))))
      result.length shouldBe 1
      result.head shouldBe defaultRequest.toDB
    }

    "only save 1 confirmation request per notification" in {
      await(repo.saveConfirmationRequest(defaultRequest))
      val dupe = await(repo.saveConfirmationRequest(defaultRequest.copy(confirmationId = ConfirmationId.random)))
      dupe shouldBe None
      val result = await(find(mongoEqual("notificationId", Codecs.toBson(notificationId))))
      result.length shouldBe 1
      result.head shouldBe defaultRequest.toDB
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
      first.head.status shouldBe ConfirmationStatus.PENDING
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

    def createConfirmationInDb(status: ConfirmationStatus, retryAfterDateTime: Option[Instant] = None) = {
      val id = ConfirmationId.random
      val confirmation = ConfirmationRequest(id, url, NotificationId.random, List.empty, status, pushedDateTime = Some(Instant.now), retryAfterDateTime = retryAfterDateTime)
      val result = await(repo.saveConfirmationRequest(confirmation))
      result shouldBe Some(id)
      confirmation
    }

    "return matching confirmations" in {
      val expectedNotification1 = createConfirmationInDb(status = ConfirmationStatus.PENDING)
      val expectedNotification2 = createConfirmationInDb(status = PENDING)

      val retryableConfirmations: Seq[ConfirmationRequest] = await(repo.fetchRetryableConfirmations.runWith(Sink.seq))

      retryableConfirmations should have size 2
      retryableConfirmations.map(_.notificationId) should contain.only(expectedNotification1.notificationId, expectedNotification2.notificationId)
    }

    "return matching confirmations ignoring bad URL records" in {
      createConfirmationInDb(status = PENDING)
      createConfirmationInDb(status = PENDING)
      saveMongoJsonWithBadUrl(defaultRequest) // This bad record should be ignored

      val retryableConfirmations: Seq[ConfirmationRequest] = await(repo.fetchRetryableConfirmations.runWith(Sink.seq))

      retryableConfirmations should have size 2
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

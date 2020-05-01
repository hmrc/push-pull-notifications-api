package uk.gov.hmrc.pushpullnotificationsapi.support

import org.scalatest.{BeforeAndAfterEach, Suite, TestSuite}
import uk.gov.hmrc.mongo.{MongoSpecSupport, Awaiting => MongoAwaiting}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

trait MongoApp extends MongoSpecSupport with BeforeAndAfterEach  {
  me: Suite with TestSuite =>

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
  }

  def dropMongoDb()(implicit ec: ExecutionContext = global): Unit =
    Awaiting.await(mongo().drop())
}

object Awaiting extends MongoAwaiting


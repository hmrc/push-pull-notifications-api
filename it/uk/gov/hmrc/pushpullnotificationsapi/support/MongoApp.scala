package uk.gov.hmrc.pushpullnotificationsapi.support

import org.scalatest.{BeforeAndAfterEach, Suite, TestSuite}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

trait MongoApp[A] extends DefaultPlayMongoRepositorySupport[A] with BeforeAndAfterEach {
  me: Suite with TestSuite =>

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
  }

  def dropMongoDb(): Unit =
    mongoDatabase.drop()

}

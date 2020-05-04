package uk.gov.hmrc.pushpullnotificationsapi.repository

import java.util.UUID

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.{DuplicateTopicException, PushSubscriber, SubscriptionType, Topic, TopicCreator}
import uk.gov.hmrc.pushpullnotificationsapi.support.MongoApp

import scala.concurrent.ExecutionContext.Implicits.global

class TopicRepositoryISpec extends UnitSpec with MongoApp with GuiceOneAppPerSuite{

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override implicit lazy val app: Application = appBuilder.build()

  def repo: TopicsRepository =
    app.injector.instanceOf[TopicsRepository]

  override def beforeEach(): Unit ={
    super.beforeEach()
    dropMongoDb()
    await(repo.ensureIndexes)
  }

  val clientId = "ClientID1"
  val topicName = "topicName"
  val topicId: String = UUID.randomUUID().toString
  val callBackEndpoint = "some/endpoint"
  val topic: Topic = Topic(topicName = topicName,
    topicId = topicId,
    topicCreator = TopicCreator(clientId),
    subscribers = List(PushSubscriber(clientId, callBackEndpoint )))

  "createTopic" should {

    "create a Topic with one PushSubscriber" in {
      val result: Unit = await(repo.createTopic(topic))
      result shouldBe ()
       val fetchedRecords = await(repo.find())
      val fetchedTopic = fetchedRecords.head
      fetchedTopic.topicName shouldBe topicName
      fetchedTopic.topicCreator.clientId shouldBe clientId
      fetchedTopic.topicId shouldBe topicId
      fetchedTopic.subscribers.size shouldBe 1
      val subscriber =  fetchedTopic.subscribers.head
      subscriber.subscriptionType shouldBe SubscriptionType.API_PUSH_SUBSCRIBER
      subscriber.clientId shouldBe clientId
      subscriber.subscriberId should not be empty

    }

    "create a Topic should allow topics for same clientId but different TopicNames" in {
      val result: Unit = await(repo.createTopic(topic))
      result shouldBe ()
      val result2: Unit = await(repo.createTopic(topic.copy(topicName = "someNewName")))
      result2 shouldBe ()
      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 2
    }

    "create a Topic should allow topics for different clientId but same TopicNames" in {
      val result: Unit = await(repo.createTopic(topic))
      result shouldBe ()
      val result2: Unit = await(repo.createTopic(topic.copy(topicCreator = topic.topicCreator.copy("someCLientId"))))
      result2 shouldBe ()
      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 2
    }

    "create a Topic should not allow creation of duplicate topics with same topicName and clientId" in {
      val result: Unit = await(repo.createTopic(topic))
      result shouldBe ()

      intercept[DuplicateTopicException] {
        await(repo.createTopic(Topic(UUID.randomUUID().toString, topicName, TopicCreator(clientId))))
      }

      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 1
    }
  }

  "getTopicByTopicNameAndClientId" should {
    "should return a list containing one topic when topic exists" in {
      val result = await(repo.getTopicByNameAndClientId(topicName, clientId))
      result.isEmpty shouldBe true
      await(repo.createTopic(topic))

      val result2 = await(repo.getTopicByNameAndClientId(topicName, clientId))
      result2.isEmpty  shouldBe false
      result2.head.topicCreator.clientId shouldBe clientId
      result2.head.topicName shouldBe topicName
      result2.size shouldBe 1
    }

    "should return an empty list  when topic does not exists (topicName)" in {

      await(repo.createTopic(topic))

      val result = await(repo.getTopicByNameAndClientId("differentTopicName", clientId))
      result.isEmpty shouldBe true
    }

    "should return an empty list  when topic does not exists (clientId)" in {

      await(repo.createTopic(topic))

      val result = await(repo.getTopicByNameAndClientId(topicName, "differentClientId"))
      result.isEmpty shouldBe true
    }
  }

}

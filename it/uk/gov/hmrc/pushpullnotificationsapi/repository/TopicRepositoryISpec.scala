package uk.gov.hmrc.pushpullnotificationsapi.repository

import java.util.UUID

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.{PushSubscriber, SubscriptionType, Topic, TopicCreator}
import uk.gov.hmrc.pushpullnotificationsapi.support.MongoApp

import scala.concurrent.ExecutionContext.Implicits.global

class TopicRepositoryISpec extends UnitSpec with MongoApp {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override implicit lazy val app: Application = appBuilder.build()

  def repo: TopicsRepository =
    app.injector.instanceOf[TopicsRepository]

  override def beforeEach() {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  val clientId = "ClientID1"
  val topicName = "topicName"
  val topicId = UUID.randomUUID().toString
  val callBackEndpoint = "some/endpoint"
  val topic: Topic = Topic(topicName = topicName,
    topicId = topicId,
    topicCreator = TopicCreator(clientId),
    subscribers = List(PushSubscriber(clientId, callBackEndpoint )))

  "createEntity" should {

    "create a Topic with one PushSubscriber" in {
      val result  = await(repo.createTopic(topic))
      result shouldBe true
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
      val result  = await(repo.createTopic(topic))
      result shouldBe true
      val result2  = await(repo.createTopic(topic.copy(topicName = "someNewName")))
      result2 shouldBe true
      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 2
    }

    "create a Topic should allow topics for different clientId but same TopicNames" in {
      val result  = await(repo.createTopic(topic))
      result shouldBe true
      val result2  = await(repo.createTopic(topic.copy(topicCreator = topic.topicCreator.copy("someCLientId"))))
      result2 shouldBe true
      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 2
    }

    "create a Topic should not allow creation of duplicate topics with same topicName and clientId" in {
      val result  = await(repo.createTopic(topic))
      result shouldBe true
      val result2  = await(repo.createTopic(Topic(UUID.randomUUID().toString, topicName, TopicCreator(clientId))))
      result2 shouldBe false
      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 1
    }
  }

}

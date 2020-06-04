package uk.gov.hmrc.pushpullnotificationsapi.repository

import java.util.UUID

import org.joda.time.DateTime
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.support.MongoApp

import scala.concurrent.ExecutionContext.Implicits.global

class TopicRepositoryISpec extends UnitSpec with MongoApp with GuiceOneAppPerSuite {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override implicit lazy val app: Application = appBuilder.build()

  def repo: TopicsRepository =
    app.injector.instanceOf[TopicsRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
    await(repo.ensureIndexes)
  }

  val clientIdStr: String = "someCLientId"
  val clientId: ClientId = ClientId(clientIdStr)
  val topicName: String = "topicName"
  val topicIdStr: String = UUID.randomUUID().toString
  val topicId: TopicId = TopicId(UUID.fromString(topicIdStr))
  val callBackEndpoint = "some/endpoint"
  val topic: Topic = Topic(topicName = topicName,
    topicId = topicId,
    topicCreator = TopicCreator(clientId))

  "createTopic" should {

    "create a Topic with one PushSubscriber" in {
      val result: Unit = await(repo.createTopic(topic))
      result shouldBe ((): Unit)
      val fetchedRecords = await(repo.find())
      val fetchedTopic = fetchedRecords.head
      fetchedTopic.topicName shouldBe topicName
      fetchedTopic.topicCreator.clientId shouldBe clientId
      fetchedTopic.topicId shouldBe topic.topicId
      fetchedTopic.subscribers.size shouldBe 0


    }

    "create a Topic should allow topics for same clientId but different TopicNames" in {
      val result: Option[TopicId] = await(repo.createTopic(topic))
      result shouldBe Some(topicId)
      val newTopicId = TopicId(UUID.randomUUID())
      val result2 = await(repo.createTopic(topic.copy(newTopicId, topicName = "someNewName")))
      result2 shouldBe Some(newTopicId)
      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 2
    }

    "create a Topic should allow topics for different clientId but same TopicNames" in {
      val result: Unit = await(repo.createTopic(topic))
      result shouldBe ((): Unit)
      val newTopicId = TopicId(UUID.randomUUID())
      val result2: Unit = await(repo.createTopic(topic.copy(newTopicId, topicCreator = topic.topicCreator.copy(ClientId(UUID.randomUUID().toString)))))
      result2 shouldBe ((): Unit)
      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 2
    }

    "create a Topic throw no errors and only create topic once when topic with same name and client id called twice" in {
      val result: Unit = await(repo.createTopic(topic))
      result shouldBe ((): Unit)

      await(repo.createTopic(Topic(TopicId(UUID.randomUUID()), topicName, TopicCreator(clientId))))


      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 1
    }

    "create a Topic should not allow creation of duplicate topics with same topic details" in {
      val result: Unit = await(repo.createTopic(topic))
      result shouldBe ((): Unit)


      await(repo.createTopic(topic))


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
      result2.isEmpty shouldBe false
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

      val result = await(repo.getTopicByNameAndClientId(topicName, ClientId(UUID.randomUUID().toString)))
      result.isEmpty shouldBe true
    }
  }
  "updateSubscribers" should {

    "subscriberId toString should match UUID string" in{
      val subscriberIdStr = UUID.randomUUID().toString
      val subscriberId = SubscriberId(UUID.fromString(subscriberIdStr))
      subscriberId.raw shouldBe subscriberIdStr
    }

    "update the subscriber list" in {
      await(repo.createTopic(topic))
      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 1

      val createdTopic = fetchedRecords.head
      createdTopic.subscribers.size shouldBe 0

      val subscribers = List(new SubscriberContainer(PushSubscriber(callBackEndpoint)))

      val updated = await(repo.updateSubscribers(createdTopic.topicId, subscribers))
      val updatedTopic = updated.head
      updatedTopic.subscribers.size shouldBe 1

      val subscriber = updatedTopic.subscribers.head.asInstanceOf[PushSubscriber]
      subscriber.callBackUrl shouldBe callBackEndpoint
      subscriber.subscribedDateTime.isBefore(DateTime.now())


    }

    "return None when the topic doesn't exist" in {
      val updated = await(repo.updateSubscribers(TopicId(UUID.randomUUID()), List(new SubscriberContainer(PushSubscriber(callBackEndpoint)))))
      updated shouldBe None

    }
  }
  "getTopicByTopicId" should {

    "return topic when topic exists" in {
      await(repo.createTopic(topic))
      val result = await(repo.findByTopicId(topic.topicId))
      result.isEmpty shouldBe false
      result.size shouldBe 1
      result.head.topicId shouldBe topic.topicId
    }

    "return empty list when topic does not exist" in {
      await(repo.createTopic(topic))
      val result = await(repo.findByTopicId(TopicId(UUID.randomUUID())))
      result.isEmpty shouldBe true
    }
  }
}

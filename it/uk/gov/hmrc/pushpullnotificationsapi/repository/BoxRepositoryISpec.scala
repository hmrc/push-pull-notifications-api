package uk.gov.hmrc.pushpullnotificationsapi.repository

import org.joda.time.DateTime
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import uk.gov.hmrc.mongo.test.PlayMongoRepositorySupport
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models._

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class BoxRepositoryISpec
  extends AsyncHmrcSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with CleanMongoCollectionSupport
    with GuiceOneAppPerSuite
    with IntegrationPatience
    with Matchers
    with PlayMongoRepositorySupport[Box] {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override implicit lazy val app: Application = appBuilder.build()

  def repo: BoxRepository =
    app.injector.instanceOf[BoxRepository]

  override protected def repository: PlayMongoRepository[Box] = app.injector.instanceOf[BoxRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  val clientIdStr: String = "someCLientId"
  val clientId: ClientId = ClientId(clientIdStr)
  val boxName: String = "boxName"
  val boxIdStr: String = UUID.randomUUID().toString
  val boxId: BoxId = BoxId(UUID.fromString(boxIdStr))
  val callBackEndpoint = "some/endpoint"
  val box: Box = Box(boxName = boxName,
    boxId = boxId,
    boxCreator = BoxCreator(clientId))

  "createBox" should {

    "create a Box with one PushSubscriber" in {
      val result: Unit = await(repo.createBox(box))
      result shouldBe ((): Unit)
      val fetchedRecords = await(repo.collection.find().toFuture())
      val fetchedBox = fetchedRecords.head
      fetchedBox.boxName shouldBe boxName
      fetchedBox.boxCreator.clientId shouldBe clientId
      fetchedBox.boxId shouldBe box.boxId
      fetchedBox.subscriber.isDefined shouldBe false
    }

    "create a Box should allow boxes for same clientId but different BoxNames" in {
      val result = await(repo.createBox(box)).asInstanceOf[BoxCreatedResult]
      result.box.boxId shouldBe boxId
      val newBoxId = BoxId(UUID.randomUUID())
      val newbox = box.copy(newBoxId, boxName = "someNewName")
      val result2 = await(repo.createBox(newbox)).asInstanceOf[BoxCreatedResult]
      result2.box shouldBe newbox
      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 2
    }

    "create a Box should allow a box for different clientId but same BoxNames" in {
      val result: Unit = await(repo.createBox(box))
      result shouldBe ((): Unit)
      val newBoxId = BoxId(UUID.randomUUID())
      val newBox = box.copy(newBoxId, boxCreator = box.boxCreator.copy(ClientId(UUID.randomUUID().toString)))
      val result2: Unit = await(repo.createBox(newBox))
      result2 shouldBe ((): Unit)
      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 2
    }

    "create a Box should throw and exception and only create box once when box with same name and client id called twice" in {
      await(repo.createBox(box))

      val result2 = await(repo.createBox(Box(BoxId(UUID.randomUUID()), boxName, BoxCreator(clientId))))
      result2.isInstanceOf[BoxCreateFailedResult] shouldBe true
      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 1
    }

    "create a Box should not allow creation of a duplicate box with same box details" in {
      val result: Unit = await(repo.createBox(box))
      result shouldBe ((): Unit)
      
      val result2 =  await(repo.createBox(box))
      result2.isInstanceOf[BoxCreateFailedResult] shouldBe true

      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 1
    }
  }

  "getBoxByBoxNameAndClientId" should {
    "should return box when it exists" in {
      val result = await(repo.getBoxByNameAndClientId(boxName, clientId))
      result shouldBe None

      await(repo.createBox(box))

      val retrievedBox = await(repo.getBoxByNameAndClientId(boxName, clientId)).get

      retrievedBox.boxCreator.clientId shouldBe clientId
      retrievedBox.boxName shouldBe boxName
    }

    "should return None when box does not exists (boxName)" in {
      await(repo.createBox(box))

      val result = await(repo.getBoxByNameAndClientId("differentBoxName", clientId))

      result shouldBe None
    }

    "should return None when box does not exist (clientId)" in {
      await(repo.createBox(box))

      val result = await(repo.getBoxByNameAndClientId(boxName, ClientId(UUID.randomUUID().toString)))

      result shouldBe None
    }
  }

  "getBoxesByClientId" should {

    val aBoxWithADifferentClientId = Box(boxName = "Another box",
      boxId = BoxId(UUID.randomUUID()),
      boxCreator = BoxCreator(ClientId("Another client")))

    "return an empty list when no boxes match" in {
      await(repo.createBox(aBoxWithADifferentClientId))

      val fetchedRecords = await(repo.getBoxesByClientId(clientId))

      fetchedRecords shouldBe empty
    }

    "return only the boxes that match the clientID" in {
      val anotherBox = box.copy(BoxId(UUID.randomUUID()), boxName = "someNewName")
      await(repo.createBox(box))
      await(repo.createBox(anotherBox))
      await(repo.createBox(aBoxWithADifferentClientId))

      val matchedBoxes = await(repo.getBoxesByClientId(clientId))
      val totalBoxes = await(repo.collection.find().toFuture())

      totalBoxes.size shouldBe (3)
      matchedBoxes should have length (2)
    }
  }

  "get all boxes" in {
    val anotherBox = box.copy(BoxId(UUID.randomUUID()), boxName = "someNewName")
    
    await(repo.createBox(box))
    await(repo.createBox(anotherBox))

    val allBoxes = await(repo.getAllBoxes())
    
    allBoxes should have length (2)
    allBoxes shouldBe List(box, anotherBox)
  }

  "updateSubscribers" should {

    "update the subscriber list" in {
      await(repo.createBox(box))

      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 1

      val createdBox = fetchedRecords.head
      createdBox.subscriber.isDefined shouldBe false

      val updated = await(repo.updateSubscriber(createdBox.boxId, new SubscriberContainer(PushSubscriber(callBackEndpoint))))
      val updatedBox = updated.head
      updatedBox.subscriber.isDefined shouldBe true

      val subscriber = updatedBox.subscriber.get.asInstanceOf[PushSubscriber]
      subscriber.callBackUrl shouldBe callBackEndpoint
      subscriber.subscribedDateTime.isBefore(DateTime.now())
    }

    "return None when the box doesn't exist" in {
      val updated = await(repo.updateSubscriber(BoxId(UUID.randomUUID()), new SubscriberContainer(PushSubscriber(callBackEndpoint))))
      updated shouldBe None
    }
  }

  "updateApplicationId" should {

    "update applicationId for box if it is not present" in {
      await(repo.createBox(box))
      val fetchedRecords = await(repo.collection.find().toFuture())
      fetchedRecords.size shouldBe 1

      val createdBox = fetchedRecords.head
      createdBox.applicationId.isDefined shouldBe false

      val appId = ApplicationId("12345")

      val updatedBox = await(repo.updateApplicationId(createdBox.boxId, appId))
      updatedBox.applicationId shouldBe Some(appId)
    }

    "return None when the box doesn't exist" in {
      intercept[RuntimeException]{
        await(repo.updateApplicationId(BoxId(UUID.randomUUID()), ApplicationId("123")))
      }
    }
  }
  "getBoxByBoxId" should {

    "return box when box exists" in {
      await(repo.createBox(box))

      val result: Option[Box] = await(repo.findByBoxId(box.boxId))

      result shouldBe Some(box)
    }

    "return empty list when box does not exist" in {
      await(repo.createBox(box))

      val result = await(repo.findByBoxId(BoxId(UUID.randomUUID())))

      result shouldBe None
    }
  }

}

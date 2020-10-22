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

class BoxRepositoryISpec extends UnitSpec with MongoApp with GuiceOneAppPerSuite {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override implicit lazy val app: Application = appBuilder.build()

  def repo: BoxRepository =
    app.injector.instanceOf[BoxRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
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
      val fetchedRecords = await(repo.find())
      val fetchedBox = fetchedRecords.head
      fetchedBox.boxName shouldBe boxName
      fetchedBox.boxCreator.clientId shouldBe clientId
      fetchedBox.boxId shouldBe box.boxId
      fetchedBox.subscriber.isDefined shouldBe false


    }

    "create a Box should allow boxs for same clientId but different BoxNames" in {
      val result: Box = await(repo.createBox(box))
      result.boxId shouldBe boxId
      val newBoxId = BoxId(UUID.randomUUID())
      val result2 = await(repo.createBox(box.copy(newBoxId, boxName = "someNewName")))
      result2 shouldBe Some(newBoxId)
      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 2
    }

    "create a Box should allow a box for different clientId but same BoxNames" in {
      val result: Unit = await(repo.createBox(box))
      result shouldBe ((): Unit)
      val newBoxId = BoxId(UUID.randomUUID())
      val result2: Unit = await(repo.createBox(box.copy(newBoxId, boxCreator = box.boxCreator.copy(ClientId(UUID.randomUUID().toString)))))
      result2 shouldBe ((): Unit)
      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 2
    }

    "create a Box throw no errors and only create box once when box with same name and client id called twice" in {
      val result: Unit = await(repo.createBox(box))
      result shouldBe ((): Unit)

      await(repo.createBox(Box(BoxId(UUID.randomUUID()), boxName, BoxCreator(clientId))))

      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 1
    }

    "create a Box should not allow creation of a duplicate box with same box details" in {
      val result: Unit = await(repo.createBox(box))
      result shouldBe ((): Unit)

      await(repo.createBox(box))

      val fetchedRecords = await(repo.find())
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
  "updateSubscribers" should {

    "update the subscriber list" in {
      await(repo.createBox(box))
      val fetchedRecords = await(repo.find())
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
      val fetchedRecords = await(repo.find())
      fetchedRecords.size shouldBe 1

      val createdBox = fetchedRecords.head
      createdBox.applicationId.isDefined shouldBe false

      val appId = ApplicationId("12345")

      val maybeApplicationId = await(repo.updateApplicationId(createdBox.boxId, appId)).flatMap(_.applicationId)
      maybeApplicationId shouldBe Some(appId)
      
    }

    "return None when the box doesn't exist" in {
      val updated = await(repo.updateApplicationId(BoxId(UUID.randomUUID()), ApplicationId("123")))
      updated shouldBe None

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

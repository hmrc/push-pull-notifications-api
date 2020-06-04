package uk.gov.hmrc.pushpullnotificationsapi.controllers

import java.util.UUID

import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Format
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{BAD_REQUEST, CREATED, NOT_FOUND, OK, UNAUTHORIZED}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models.{Topic, TopicId}
import uk.gov.hmrc.pushpullnotificationsapi.repository.{NotificationsRepository, TopicsRepository}
import uk.gov.hmrc.pushpullnotificationsapi.support.{AuditService, AuthService, MongoApp, ServerBaseISpec}

import scala.concurrent.ExecutionContext.Implicits.global

class NotificationsControllerISpec extends ServerBaseISpec with BeforeAndAfterEach with MongoApp with AuthService with AuditService{
  this: Suite with ServerProvider =>
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  def topicsRepo:  TopicsRepository= app.injector.instanceOf[TopicsRepository]
  def notificationRepo: NotificationsRepository = app.injector.instanceOf[NotificationsRepository]

  override def beforeEach(): Unit ={
    super.beforeEach()
    dropMongoDb()
    primeAuditService()
    await(topicsRepo.ensureIndexes)
    await(notificationRepo.ensureIndexes)
  }

  protected override def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "metrics.enabled"                 -> true,
        "auditing.enabled"                -> true,
        "auditing.consumer.baseUri.host"  -> wireMockHost,
        "auditing.consumer.baseUri.port"  -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  val url = s"http://localhost:$port"

  val topicName = "mytopicName"
  val clientId = "someClientId"
  val createTopicJsonBody =raw"""{"clientId": "$clientId", "topicName": "$topicName"}"""
  val createTopic2JsonBody =raw"""{"clientId": "zzzzzzzzzz", "topicName": "bbyybybyb"}"""

  val updateSubcribersJsonBodyWithIds: String = raw"""{ "subscribers":[{
                                             |     "subscriberType": "API_PUSH_SUBSCRIBER",
                                             |     "callBackUrl":"somepath/firstOne",
                                             |     "subscriberId": "74d3ef1e-9b6f-4e75-897d-217cc270140f"
                                             |   }]
                                             |}
                                             |""".stripMargin

  val validHeadersJson = List("Content-Type" -> "application/json",  ("User-Agent" -> "api-subscription-fields"))
  val validHeadersJsonWithNoUserAgent = List("Content-Type" -> "application/json")
  val validHeadersXml = List("Content-Type" -> "application/xml")

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def doPost(urlString: String, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(urlString)
      .withHttpHeaders(headers: _*)
      .post(jsonBody)
      .futureValue


  def doPut(urlString: String, jsonBody: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(urlString)
      .withHttpHeaders(headers: _*)
      .put(jsonBody)
      .futureValue

  def doGet(urlString: String, headers: List[(String, String)]): WSResponse =
    wsClient
      .url(urlString)
      .withHttpHeaders(headers: _*)
      .get
      .futureValue
  // need to clean down mongo then run two

  def createTopicAndReturn(): Topic = {
    val result = doPut( s"$url/topics", createTopicJsonBody, validHeadersJson)
    result.status shouldBe CREATED
    await(topicsRepo.findAll().head)
  }

  def createNotifications(topicId: TopicId, numberToCreate: Int): Unit ={
    for(_ <- 0 until numberToCreate){
      val result = doPost( s"$url/notifications/topics/${topicId.raw}", "{}", validHeadersJson)
      result.status shouldBe CREATED
    }


  }
  "NotificationsController" when {

    "POST /notification/topics/[topicId]" should {
      "respond with 201 when notification created for valid json and json content type" in {
       val topic =  createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${topic.topicId.raw}", "{}", validHeadersJson)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 201 when notification created for valid xml and xml content type" in {
        val topic = createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${topic.topicId.raw}", "<somNode/>", validHeadersXml)
        result.status shouldBe CREATED
        validateStringIsUUID(result.body)
      }

      "respond with 400 when for valid xml and but json content type" in {
        val topic = createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${topic.topicId.raw}", "<somNode/>", validHeadersJson)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 400 when for valid json and but xml content type" in {
        val topic = createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${topic.topicId.raw}", "{}", validHeadersXml)
        result.status shouldBe BAD_REQUEST
      }

      "respond with 400 when unknown content type sent in request" in {
        val topic = createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${topic.topicId.raw}", "{}", List("ContentType" -> "text/plain"))
        result.status shouldBe BAD_REQUEST
      }

      "respond with 404 when unknown / non existent topic id sent" in {
        createTopicAndReturn()
        val result = doPost( s"$url/notifications/topics/${UUID.randomUUID().toString}", "{}", validHeadersJson)
        result.status shouldBe NOT_FOUND
      }
    }

    "GET /notification/topics/[topicId]" should {
      "respond with 201 when notification created for valid json and json content type" in {
        primeAuthServiceSuccess(clientId,"{\"authorise\" : [ ], \"retrieve\" : [ \"optionalClientId\" ]}")
        val topic =  createTopicAndReturn()
        createNotifications(topic.topicId, 4)
        val result: WSResponse = doGet( s"$url/notifications/topics/${topic.topicId.raw}", validHeadersJson)
        result.status shouldBe OK
      }
    }

    "respond with 401 on create when clientId returned from auth does not match" in {
      primeAuthServiceSuccess("UnknownClientId","{\"authorise\" : [ ], \"retrieve\" : [ \"optionalClientId\" ]}")
      val topic =  createTopicAndReturn()
      createNotifications(topic.topicId, 4)
      val result: WSResponse = doGet( s"$url/notifications/topics/${topic.topicId.raw}", validHeadersJson)
      result.status shouldBe UNAUTHORIZED
    }

    "respond with 401 on create when no clientID in response from auth" in {
      primeAuthServiceNoCLientId("{\"authorise\" : [ ], \"retrieve\" : [ \"optionalClientId\" ]}")
      val topic =  createTopicAndReturn()
      createNotifications(topic.topicId, 4)
      val result: WSResponse = doGet( s"$url/notifications/topics/${topic.topicId.raw}", validHeadersJson)
      result.status shouldBe UNAUTHORIZED
    }
    "respond with 401 when authorisation fails" in {
      primeAuthServiceFail()
      val topic =  createTopicAndReturn()
      createNotifications(topic.topicId, 4)
      val result: WSResponse = doGet( s"$url/notifications/topics/${topic.topicId.raw}", validHeadersJson)
      result.status shouldBe UNAUTHORIZED
    }
  }
}

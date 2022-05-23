package uk.gov.hmrc.pushpullnotificationsapi.repository.models

import play.api.libs.json.JsResult
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.Box
import uk.gov.hmrc.pushpullnotificationsapi.models.BoxId

import java.util.UUID
import uk.gov.hmrc.pushpullnotificationsapi.models.BoxCreator
import uk.gov.hmrc.pushpullnotificationsapi.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models.ApplicationId
import uk.gov.hmrc.pushpullnotificationsapi.models.Subscriber
import uk.gov.hmrc.pushpullnotificationsapi.models.PushSubscriber
import uk.gov.hmrc.pushpullnotificationsapi.models.PullSubscriber
import play.api.libs.json.OFormat
import org.joda.time.DateTime

class BoxFormatSpec extends AsyncHmrcSpec {

  val minimumValidJson: JsValue = Json.parse(
    """{
     "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
     "boxName":"boxName",
     "boxCreator":{
        "clientId":"someClientId"
     }
  }"""
  )

  val maximumValidJson: JsValue = Json.parse(
    """{
     "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
     "boxName":"boxName",
     "clientManaged":true,
     "boxCreator":{
        "clientId":"someClientId"
     }
  }"""
  )

  val clientManagedTrueJson: JsValue = Json.parse(
    """{
     "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
     "boxName":"boxName",
     "clientManaged":true,
     "boxCreator":{
        "clientId":"someClientId"
     }
  }"""
  )

  val clientManagedFalseJson: JsValue = Json.parse(
    """{
     "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
     "boxName":"boxName",
     "clientManaged":false,
     "boxCreator":{
        "clientId":"someClientId"
     }
  }"""
  )

  val missingNameJson: JsValue = Json.parse(
    """{
     "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
     "boxCreator":{
        "clientId":"someClientId"
     }
  }"""
  )

  val nullNameJson: JsValue = Json.parse(
    """{
     "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
     "boxName": null,
     "applicationId": "1ld6sj4k-1a2b-3c4d-5e6f-1e651bbb49a8",
     "boxCreator":{
        "clientId":"someClientId"
     }
  }"""
  )

  implicit val format = BoxFormat
  val validBox: Box = Json.fromJson[Box](minimumValidJson).get
  val clientManagedBox: Box = Json.fromJson[Box](clientManagedTrueJson).get
  val missingBoxName: JsResult[Box] = Json.fromJson[Box](missingNameJson)
  val nullNameBox: JsResult[Box] = Json.fromJson[Box](nullNameJson)

  val testerValue = BoxFormat.writes(validBox)

  "BoxFormatFactory" when {
    "reads is used by Json.fromJson" should {
      "correctly assign the boxId" in {
        validBox.boxId shouldBe BoxId(
          UUID.fromString("ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8")
        )
      }

      "correctly assign the boxName" in {
        validBox.boxName shouldBe ("boxName")
      }

      "correctly assign the clientId" in {
        validBox.boxCreator.clientId.value shouldBe ("someClientId")
      }

      "result in an error if the boxName is missing" in {
        missingBoxName.isError shouldBe true
      }

      "result in an error if the boxName is null" in {
        nullNameBox.isError shouldBe true
      }

      "default clientManaged to false when not present" in {
        validBox.clientManaged shouldBe false
      }

      "use clientManaged value when present" in {
        clientManagedBox.clientManaged shouldBe true
      }
    }

    "writes is used correctly to generate json" should {
      "create a valid box when the client managed field is provided" in {
        // BoxFormat.writes(clientManagedBox) shouldBe (clientManagedTrueJson)
        val box = Box(
          BoxId(UUID.fromString("ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8")),
          "boxName",
          BoxCreator(ClientId("someClientId")),
          Some(ApplicationId("1ld6sj4k-1a2b-3c4d-5e6f-1e651bbb49a8")),
          Some(
            PullSubscriber("callback", DateTime.parse("2010-06-30T01:20+02:00"))
          ),
          true
        )

        val expectedJson = Json.parse(
          """{
     "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
     "boxName":"boxName",
     "boxCreator":{
        "clientId":"someClientId"
     },
     "applicationId":{"value":"1ld6sj4k-1a2b-3c4d-5e6f-1e651bbb49a8"},
     "subscriber":{"callBackUrl":"callback","subscribedDateTime":{"$date":1277853600000},"subscriptionType":"API_PULL_SUBSCRIBER"},
     "clientManaged":true
  }"""
        )

        BoxFormat.writes(box) shouldBe expectedJson
      }

      "result in an error if the boxName is null" in {
        val box = Box(
          BoxId(UUID.fromString("ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8")),
          "boxName",
          BoxCreator(ClientId("someClientId")),
          Some(ApplicationId("1ld6sj4k-1a2b-3c4d-5e6f-1e651bbb49a8")),
          Some(PullSubscriber("callback")),
          true
        )
        // val jsObject = BoxFormat.writes(box)

        // implicit val boxFormats: OFormat[Box] = Json.format[Box]

        val json = Json.toJson(box)

        print(json)
      }
    }
  }
}

// Mongo id field has been removed from Json, do we care about it?
// Ensure subscriptionType is read correctly.

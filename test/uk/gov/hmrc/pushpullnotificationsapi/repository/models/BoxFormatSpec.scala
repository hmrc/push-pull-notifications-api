package uk.gov.hmrc.pushpullnotificationsapi.repository.models

import play.api.libs.json.JsResult
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.Box
import uk.gov.hmrc.pushpullnotificationsapi.models.BoxId

import java.util.UUID

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

  val clientManagedJson: JsValue = Json.parse(
    """{
     "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
     "boxName":"boxName",
     "clientManaged":true,
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
     "boxCreator":{
        "clientId":"someClientId"
     }
  }"""
  )

  implicit val format = BoxFormat
  val validBox: Box = Json.fromJson[Box](minimumValidJson).get
  val clientManagedBox: Box = Json.fromJson[Box](clientManagedJson).get
  val missingBoxName: JsResult[Box] = Json.fromJson[Box](missingNameJson)
  val nullNameBox: JsResult[Box] = Json.fromJson[Box](nullNameJson)

  "BoxFormatFactory" when {
    "reads is used by Json.fromJson" should {
      "correctly assign the boxId" in {
        validBox.boxId shouldBe BoxId(UUID.fromString("ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8"))
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

      "result in none if the boxName is null" in {
        nullNameBox.isError shouldBe true
      }

      "default clientManaged to false when not present" in {
        validBox.clientManaged shouldBe false
      }

      "use clientManaged value when present" in {
        clientManagedBox.clientManaged shouldBe true
      }
    }
  }
}

// Mongo id field has been removed from Json, do we care about it?

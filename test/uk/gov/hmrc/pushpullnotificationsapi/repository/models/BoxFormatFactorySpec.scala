package uk.gov.hmrc.pushpullnotificationsapi.repository.models

import play.api.libs.json.{JsResult, JsValue, Json}
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.Box

class BoxFormatFactorySpec extends AsyncHmrcSpec {

  implicit val format = BoxFormat

  "BoxFormatFactory" when {
    "reads is used by Json.fromJson" should {
      val jsonString: JsValue = Json.parse("""{
      "boxId" : "ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
      "boxName" : "boxName",
      "boxCreator" : {
          "clientId" : "someClientId"
      }
   }""")

      "correctly assign the boxName" in {
        val boxFromJson: JsResult[Box] = Json.fromJson[Box](jsonString)
        val box = boxFromJson.get
        box.boxName shouldBe ("boxName")
      }

      "result in an error if the boxName is missing" in {
        val jsonString: JsValue = Json.parse("""{
      "boxId" : "ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
      "boxCreator" : {
          "clientId" : "someClientId"
      }
   }""")

        val result = Json.fromJson[Box](jsonString)

        result.isError shouldBe true
      }

      "default clientManaged to false when not present" in {
        val boxFromJson: JsResult[Box] = Json.fromJson[Box](jsonString)
        val box = boxFromJson.get
        box.clientManaged shouldBe false
      }

      "use clientManaged value when present" in {
        val jsonString: JsValue = Json.parse("""{
      "boxId" : "ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
      "boxName" : "boxName",
      "clientManaged" : true,
      "boxCreator" : {
          "clientId" : "someClientId"
      }
   }""")

        val boxFromJson: JsResult[Box] = Json.fromJson[Box](jsonString)
        val box = boxFromJson.get
        box.clientManaged shouldBe true
      }
    }
  }
}

// Mongo id field has been removed from Json, do we care about it?

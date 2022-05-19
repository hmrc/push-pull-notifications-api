package uk.gov.hmrc.pushpullnotificationsapi.repository.models

import play.api.libs.json.{JsResult, JsValue, Json}
import uk.gov.hmrc.pushpullnotificationsapi.AsyncHmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.Box

class BoxFormatFactorySpec extends AsyncHmrcSpec {
  "BoxFormatFactory" when {
    "create" should {
      val jsonString: JsValue = Json.parse(
        """{
      "boxId" : "ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
      "boxName" : "boxName",
      "boxCreator" : {
          "clientId" : "someCLientId"
      }
   }""")

      "Create should generate a format which can read and create a box" in {
        implicit val format = BoxFormat
        val boxFromJson: JsResult[Box] = Json.fromJson[Box](jsonString)
        val box = boxFromJson.get
        box.boxName shouldBe("boxName")
      }
    }
  }
}

// Mongo id field has been removed from Json, do we care about it?

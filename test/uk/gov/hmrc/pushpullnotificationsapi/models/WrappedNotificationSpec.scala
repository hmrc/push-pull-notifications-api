package uk.gov.hmrc.pushpullnotificationsapi.models

import uk.gov.hmrc.apiplatform.modules.common.utils.JsonFormattersSpec
import play.api.libs.json._

class WrappedNotificationSpec extends JsonFormattersSpec{
  
  "WrappedNotification" should {
    val aBody = """some text that would be json"""
    val wrappedNotificationWithHeaders = WrappedNotification(aBody, "application/json", List(PrivateHeader("n1", "v1"), PrivateHeader("n2","v2")))

    "write json" when {
      "there are no private headers" in {
        testToJsonValues(WrappedNotification(aBody, "application/json", List.empty))(
          ("body" -> JsString(aBody)), ("contentType" -> JsString("application/json")), ("privateHeaders" -> JsArray.empty)
        )
      }


      "there are private headers" in {
        testToJsonValues(wrappedNotificationWithHeaders)(
          ("body" -> JsString(aBody)), ("contentType" -> JsString("application/json")), ("privateHeaders" -> JsArray(Seq(asObj("name"->"n1", "value"->"v1"), asObj("name" -> "n2", "value" -> "v2"))))
        )
      }
    }

    "read json" when {
      "there are private headers" in {
        testFromJson(s"""{
          "body": "$aBody",
          "contentType": "application/json",
          "privateHeaders": [
            {
              "name": "n1",
              "value": "v1"
            },
            {
              "name": "n2",
              "value": "v2"
            }
            ]
          }"""
        )(wrappedNotificationWithHeaders)
      }

      "there are empty private headers" in {
        testFromJson(s"""{
          "body": "$aBody",
          "contentType": "application/json",
          "privateHeaders": []
          }"""
        )(wrappedNotificationWithHeaders.copy(privateHeaders = List.empty))
      }    

      "there are no private headers" in {
        testFromJson(s"""{
          "body": "$aBody",
          "contentType": "application/json"
          }"""
        )(wrappedNotificationWithHeaders.copy(privateHeaders = List.empty))
      }   
    }
  }
}

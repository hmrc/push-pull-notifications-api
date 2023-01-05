/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.pushpullnotificationsapi.repository.models

import org.joda.time.DateTime
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import uk.gov.hmrc.pushpullnotificationsapi.HmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.BoxFormat._
import uk.gov.hmrc.pushpullnotificationsapi.repository.models.PlayHmrcMongoFormatters._

import java.util.UUID

class BoxFormatSpec extends HmrcSpec {

  "BoxFormat" when {
    "reading JSON with all fields present" should {

      val json: JsValue = Json.parse(
        """{
          | "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
          | "boxName":"boxName",
          | "clientManaged":true,
          | "applicationId":"71ef5626-2f75-429c-b8b3-23bbdf5f0084",
          | "boxCreator":{
          |  "clientId":"someClientId"
          | },
          | "subscriber":{
          |  "callBackUrl":"callback",
          |  "subscribedDateTime":{
          |   "$date":{"$numberLong":"1277853600000"}
          |  },
          |  "subscriptionType":"API_PULL_SUBSCRIBER"
          | }
          |}""".stripMargin
      )

      val box = Json.fromJson[Box](json).get

      "correctly assign the boxId value" in {
        box.boxId shouldBe BoxId(
          UUID.fromString("ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8")
        )
      }

      "correctly assign the boxName value" in {
        box.boxName shouldBe ("boxName")
      }

      "correctly assign the boxCreator value" in {
        box.boxCreator shouldBe BoxCreator(ClientId("someClientId"))
      }

      "correctly assign the clientManaged value" in {
        box.clientManaged shouldBe true
      }

      "correctly assign the applicationId value" in {
        box.applicationId shouldBe Some(
          ApplicationId("71ef5626-2f75-429c-b8b3-23bbdf5f0084")
        )
      }

      "correctly assign the subscriber value" in {
        box.subscriber shouldBe Some(
          PullSubscriber("callback", DateTime.parse("2010-06-29T23:20+00:00"))
        )
      }
    }

    "reading JSON with optional fields missing" should {

      val json: JsValue = Json.parse(
        """{
          | "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
          | "boxName":"boxName",
          | "boxCreator":{
          |  "clientId":"someClientId"
          | }
          |}""".stripMargin
      )

      val box = Json.fromJson[Box](json).get

      "default clientManaged to false" in {
        box.clientManaged shouldBe false
      }

      "default applicationId to None" in {
        box.applicationId shouldBe None
      }

      "default subscriber to None" in {
        box.subscriber shouldBe None
      }
    }

    "reading JSON with required fields missing" should {
      "result in an error if the boxName is missing" in {
        val json: JsValue = Json.parse(
          """{
            | "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
            | "boxCreator":{
            |  "clientId":"someClientId"
            | }
            |}""".stripMargin
        )

        val box = Json.fromJson[Box](json)

        box.isError shouldBe true
      }

      "result in an error if the boxName is null" in {
        val json: JsValue = Json.parse(
          """{
            | "boxName":null,
            | "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
            | "boxCreator":{
            |  "clientId":"someClientId"
            | }
            |}""".stripMargin
        )

        val box = Json.fromJson[Box](json)
        box.isError shouldBe true
      }

      "result in an error if the boxId is missing" in {
        val json: JsValue = Json.parse(
          """{
            | "boxName":"boxName",
            | "boxCreator":{
            |  "clientId":"someClientId"
            | }
            |}""".stripMargin
        )

        val box = Json.fromJson[Box](json)

        box.isError shouldBe true
      }

      "result in an error if the boxId is null" in {
        val json: JsValue = Json.parse(
          """{
            | "boxName":"boxName",
            | "boxId":null,
            | "boxCreator":{
            |  "clientId":"someClientId"
            | }
            |}""".stripMargin
        )

        val box = Json.fromJson[Box](json)
        box.isError shouldBe true
      }

      "result in an error if the boxCreator is missing" in {
        val json: JsValue = Json.parse(
          """{
            | "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
            | "boxName":"boxName"
            |}""".stripMargin
        )

        val box = Json.fromJson[Box](json)

        box.isError shouldBe true
      }

      "result in an error if the boxCreator is null" in {
        val json: JsValue = Json.parse(
          """{
            | "boxName":"boxName",
            | "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
            | "boxCreator":null
            |}""".stripMargin
        )

        val box = Json.fromJson[Box](json)
        box.isError shouldBe true
      }
    }

    "writing JSON" should {
      "write all fields correctly into JSON" in {
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
            | "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
            | "boxName":"boxName",
            | "boxCreator":{"clientId":"someClientId"},
            | "applicationId":"1ld6sj4k-1a2b-3c4d-5e6f-1e651bbb49a8",
            | "subscriber":{"callBackUrl":"callback","subscribedDateTime":{"$date":{"$numberLong":"1277853600000"}},"subscriptionType":"API_PULL_SUBSCRIBER"},
            | "clientManaged":true
            |}""".stripMargin
        )

        boxFormats.writes(box) shouldBe expectedJson
      }

      "handle optional fields being None" in {
        val box = Box(
          BoxId(UUID.fromString("ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8")),
          "boxName",
          BoxCreator(ClientId("someClientId")),
          None,
          None
        )

        val expectedJson = Json.parse(
          """{
            | "boxId":"ceb081f7-6e89-4f6a-b6ba-1e651aaa49a8",
            | "boxName":"boxName",
            | "boxCreator":{"clientId":"someClientId"},
            | "clientManaged":false
            |}""".stripMargin
        )

        boxFormats.writes(box) shouldBe expectedJson
      }
    }
  }
}

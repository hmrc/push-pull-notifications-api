package uk.gov.hmrc.pushpullnotificationsapi.repository.models

import play.api.libs.json.Json
import uk.gov.hmrc.pushpullnotificationsapi.HmrcSpec
import uk.gov.hmrc.pushpullnotificationsapi.models.InstantFormatter._
import uk.gov.hmrc.pushpullnotificationsapi.models.ResponseFormatters._

import java.time.Instant

class JsonFormatterSpec extends HmrcSpec {

  "jsonFormatter" should {

    val dateTime = "2023-02-01T18:18:31.000+0000"

    "only have 3 nano values" in {
      val offsetInstant = lenientFormatter.parse(dateTime, a => Instant.from(a))
      val offsetString = Json.toJson(offsetInstant).as[String]
      offsetString shouldBe dateTime
    }
  }
}

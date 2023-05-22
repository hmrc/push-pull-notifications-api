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

package uk.gov.hmrc.pushpullnotificationsapi.models

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField._
import java.time.{Instant, ZoneId}

import play.api.libs.json._

import uk.gov.hmrc.pushpullnotificationsapi.models.notifications._

object InstantFormatter {

  val lenientFormatter: DateTimeFormatter = new DateTimeFormatterBuilder()
    .parseLenient()
    .parseCaseInsensitive()
    .appendPattern("uuuu-MM-dd['T'HH:mm:ss[.SSS][Z]['Z']]")
    .parseDefaulting(NANO_OF_SECOND, 0)
    .parseDefaulting(SECOND_OF_MINUTE, 0)
    .parseDefaulting(MINUTE_OF_HOUR, 0)
    .parseDefaulting(HOUR_OF_DAY, 0)
    .toFormatter
    .withZone(ZoneId.of("UTC"))

  val instantReads: Reads[Instant] = Reads.instantReads(lenientFormatter)

  val instantWrites: Writes[Instant] = Writes.temporalWrites(new DateTimeFormatterBuilder()
    .appendPattern("uuuu-MM-dd'T'HH:mm:ss.SSSZ")
    .toFormatter
    .withZone(ZoneId.of("UTC")))

  object Implicits {
    implicit val instantFormat: Format[Instant] = Format(instantReads, instantWrites)
  }
}

object ResponseFormatters {
  import InstantFormatter.Implicits._

  implicit val boxFormats: OFormat[Box] = Json.format[Box]
  implicit val notificationFormatter: OFormat[Notification] = Json.format[Notification]
  implicit val notificationResponseFormatter: OFormat[NotificationResponse] = Json.format[NotificationResponse]
}

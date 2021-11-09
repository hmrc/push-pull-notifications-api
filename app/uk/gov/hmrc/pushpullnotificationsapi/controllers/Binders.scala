/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.pushpullnotificationsapi.controllers

import play.api.Logger
import play.api.mvc.{PathBindable, QueryStringBindable}
import uk.gov.hmrc.pushpullnotificationsapi.models.{BoxId, ClientId}

import java.util.UUID
object Binders {
 val logger = Logger("binders")
  implicit object clientIdQueryStringBindable extends QueryStringBindable.Parsing[ClientId](
    s => ClientId(s),
    _.value,
    (key: String, e: Exception) => "Cannot parse parameter %s as ClientId: %s".format(key, e.getMessage)
  )

  implicit object boxIdPathBindable extends PathBindable.Parsing[BoxId](
    s => BoxId(UUID.fromString(s)),
    _.value.toString,
    (key: String, e: Exception) => {
      logger.info("Cannot parse parameter %s as BoxId: %s".format(key, e.getMessage))
      "Box ID is not a UUID"
    }
  )

  implicit object clientIdPathBindable extends PathBindable.Parsing[ClientId](
    s => ClientId(s),
    _.value,
    (key: String, e: Exception) => "Cannot parse parameter %s as ClientId: %s".format(key, e.getMessage)
  )
}

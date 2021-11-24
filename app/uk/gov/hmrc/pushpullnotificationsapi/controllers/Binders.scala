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

import java.util.UUID
import uk.gov.hmrc.pushpullnotificationsapi.models.{BoxId, ClientId}

import scala.util.Try
object Binders {
  val logger = Logger("binders")

  private def boxIdFromString(text: String): Either[String, BoxId] = {
    Try(UUID.fromString(text))
      .toOption
      .toRight({   logger.info("Cannot parse parameter %s as BoxId".format(text))
        "Box ID is not a UUID"
        })
      .map(BoxId(_))
  }

  implicit def clientIdQueryStringBindable(implicit textBinder: QueryStringBindable[String]) = new QueryStringBindable[ClientId] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ClientId]] = {
      for {
        text <- textBinder.bind(key, params)
      } yield {
        text match {
          case Right(clientId) => Right(ClientId(clientId))
          case _              =>  Left("Unable to bind a clientId")
        }
      }
    }

    override def unbind(key: String, clientId: ClientId): String = {
      textBinder.unbind(key, clientId.value)
    }
  }

  implicit def boxIdPathBindable(implicit textBinder: PathBindable[String]): PathBindable[BoxId] = new PathBindable[BoxId] {
    override def bind(key: String, value: String): Either[String, BoxId] = {
      textBinder.bind(key, value).flatMap(boxIdFromString)
    }

    override def unbind(key: String, boxId: BoxId): String = {
      boxId.value.toString()
    }
  }

  implicit def clientIdPathBinder(implicit textBinder: PathBindable[String]): PathBindable[ClientId] = new PathBindable[ClientId] {

    override def bind(key: String, value: String): Either[String, ClientId] = {
      textBinder.bind(key, value).map(ClientId(_))
    }

    override def unbind(key: String, clientId: ClientId): String = {
      clientId.value
    }
  }
}

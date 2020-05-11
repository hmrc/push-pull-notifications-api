/*
 * Copyright 2020 HM Revenue & Customs
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

import org.joda.time.DateTime
import play.api.libs.json.{Format, JodaReads, JodaWrites, JsError, JsResult, JsString, JsSuccess, JsValue, Json, OFormat, Reads, Writes}
import uk.gov.hmrc.play.json.Union


object JodaDateFormats {
  val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
  implicit val JodaDateReads: Reads[org.joda.time.DateTime] = JodaReads.jodaDateReads(dateFormat)
  implicit val JodaDateWrites: Writes[org.joda.time.DateTime] = JodaWrites.jodaDateWrites(dateFormat)
  implicit val JodaDateTimeFormat: Format[DateTime] = Format(JodaDateReads, JodaDateWrites)
}

object ReactiveMongoFormatters {
  implicit val dateFormats = JodaDateFormats.JodaDateTimeFormat
  implicit val pushSubscriberFormats: OFormat[PushSubscriber] = Json.format[PushSubscriber]
  implicit val formatTopicCreator: Format[TopicCreator] = Json.format[TopicCreator]
  implicit val formatSubscriber: Format[Subscriber] = Union.from[Subscriber]("subscriptionType")
    .and[PushSubscriber](SubscriptionType.API_PUSH_SUBSCRIBER.toString)
    .format
  implicit val formats: OFormat[Topic] = Json.format[Topic]

}

object RequestFormatters {
  implicit val createTopicRequestFormatter: OFormat[CreateTopicRequest] = Json.format[CreateTopicRequest]
  implicit val subscribersRequestFormatter: OFormat[SubscribersRequest] = Json.format[SubscribersRequest]
  implicit val updateSubscribersRequestFormatter: OFormat[UpdateSubscribersRequest] = Json.format[UpdateSubscribersRequest]
}

class InvalidEnumException(className: String, input:String)
  extends RuntimeException(s"Enumeration expected of type: '$className', but it does not contain '$input'")

object EnumJson {

  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
    def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) =>
        try {
          JsSuccess(enum.withName(s))
        } catch {
          case _: NoSuchElementException => throw new InvalidEnumException(enum.getClass.getSimpleName, s)
        }
      case _ => JsError("String value expected")
    }
  }

  implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
    def writes(v: E#Value): JsValue = JsString(v.toString)
  }

  implicit def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = {
    Format(enumReads(enum), enumWrites)
  }



}

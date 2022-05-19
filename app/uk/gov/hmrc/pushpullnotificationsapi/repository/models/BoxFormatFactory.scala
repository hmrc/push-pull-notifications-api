package uk.gov.hmrc.pushpullnotificationsapi.repository.models

import org.joda.time.DateTime
import play.api.libs.json.{Format, JsObject, JsResult, JsValue, Json, OFormat}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.pushpullnotificationsapi.models.{ApplicationId, Box, BoxCreator, BoxId, ClientId, PullSubscriber, PushSubscriber, Subscriber}

object BoxFormat extends OFormat[Box] {
  implicit val applicationIdFormat = Json.format[ApplicationId]
  implicit val clientIdFormatter: Format[ClientId] = Json.valueFormat[ClientId]
  implicit val boxIdFormatter: Format[BoxId] = Json.valueFormat[BoxId]
  implicit val boxCreatorFormat = Json.format[BoxCreator]
  implicit val pushSubscriberFormat = Json.format[PushSubscriber]
  implicit val pullSubscriberFormat = Json.format[PullSubscriber]
  implicit val subscriberFormat = Json.format[Subscriber]
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  override def writes(o: Box): JsObject = { Json.writes[Box].writes(o) }
  override def reads(json: JsValue): JsResult[Box] = { Json.format[Box].reads(json) }
}

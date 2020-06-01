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

package uk.gov.hmrc.pushpullnotificationsapi.controllers.actionbuilders

import javax.inject.{Inject, Singleton}
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.mvc.Results.NotFound
import play.api.mvc.{ActionRefiner, Request, Result}
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus
import uk.gov.hmrc.pushpullnotificationsapi.models.{NotificationQueryParams, ValidatedNotificationQueryRequest}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


@Singleton
class ValidateNotificationQueryParamsAction @Inject()(implicit ec: ExecutionContext)
  extends ActionRefiner[Request, ValidatedNotificationQueryRequest]  with HttpErrorFunctions {
  actionName =>

  override def executionContext: ExecutionContext = ec
  override def refine[A](request: Request[A]): Future[Either[Result, ValidatedNotificationQueryRequest[A]]]  = Future.successful {
    validateNotificationQueryParams(request) match{
      case Right(value) => Right(ValidatedNotificationQueryRequest[A](value, request))
      case Left(error) => Left(error)
    }
  }

  val statusParamKey = "status"
  val fromDateParamKey = "from_date"
  val toDateParamKey = "to_date"


  private def validateNotificationQueryParams[A](request: Request[A]): Either[Result, NotificationQueryParams] = {

    for {
      statusVal <- validateStatusParamValue(request.getQueryString(statusParamKey))
      fromDateVal <- validateDateParamValue(request.getQueryString(fromDateParamKey))
      toDateVal <- validateDateParamValue(request.getQueryString(toDateParamKey))
    } yield NotificationQueryParams(statusVal, fromDateVal, toDateVal)
  }

  private def validateStatusParamValue(maybeStatusStr: Option[String]): Either[Result, Option[NotificationStatus]] = {
    maybeStatusStr match {
      case Some(statusVal) => Try[NotificationStatus]{NotificationStatus.withName(statusVal)} match {
        case Success(x) => Right(Some(x))
        case Failure(_) => Logger.info(s"Invalid Status Param provided: $statusVal")
          Left(NotFound)
      }
      case _ => Right(None)
    }
  }


  def validateDateParamValue(maybeString: Option[String]): Either[Result, Option[DateTime]] = {
    maybeString match {
      case Some(stringVal) =>
        Try[DateTime]{
          DateTime.parse(stringVal, ISODateTimeFormat.dateTimeParser())
        } match {
          case Success(parseDate) => Right(Some(parseDate.withZone(DateTimeZone.UTC)))
          case Failure(_) =>  Logger.info(s"Unparsable DateValue Param provided: $stringVal")
            Left(NotFound)
        }
      case None => Right(None)
    }
  }

}

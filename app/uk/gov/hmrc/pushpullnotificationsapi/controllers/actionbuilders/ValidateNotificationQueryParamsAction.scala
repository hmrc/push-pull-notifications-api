/*
 * Copyright 2024 HM Revenue & Customs
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

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import play.api.mvc.Results.BadRequest
import play.api.mvc.{ActionRefiner, Result}
import uk.gov.hmrc.http.HttpErrorFunctions

import uk.gov.hmrc.apiplatform.modules.common.domain.services.InstantJsonFormatter.lenientFormatter
import uk.gov.hmrc.pushpullnotificationsapi.models._
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.NotificationStatus
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

@Singleton
class ValidateNotificationQueryParamsAction @Inject() (implicit ec: ExecutionContext)
    extends ActionRefiner[AuthenticatedNotificationRequest, ValidatedNotificationQueryRequest]
    with HttpErrorFunctions
    with ApplicationLogger {
  actionName =>

  override def executionContext: ExecutionContext = ec

  override def refine[A](request: AuthenticatedNotificationRequest[A]): Future[Either[Result, ValidatedNotificationQueryRequest[A]]] = Future.successful {
    validateNotificationQueryParams(request) match {
      case Right(value) => Right(ValidatedNotificationQueryRequest[A](request.clientId, value, request.request))
      case Left(error)  => Left(error)
    }
  }

  val statusParamKey = "status"
  val fromDateParamKey = "fromDate"
  val toDateParamKey = "toDate"
  val validKeys = List(statusParamKey, fromDateParamKey, toDateParamKey)

  def validateQueryParamsKeys(queryParams: Map[String, Seq[String]]): Either[Result, Boolean] = {
    if (queryParams.nonEmpty) {
      if (!queryParams.keys.forall(validKeys.contains(_))) {
        Left(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Invalid / Unknown query parameter provided")))
      } else Right(true)
    } else {
      Right(true)
    }
  }

  private def validateNotificationQueryParams[A](request: AuthenticatedNotificationRequest[A]): Either[Result, NotificationQueryParams] = {
    for {
      _ <- validateQueryParamsKeys(request.request.queryString)
      statusVal <- validateStatusParamValue(request.request.getQueryString(statusParamKey))
      fromDateVal <- validateDateParamValue(request.request.getQueryString(fromDateParamKey))
      toDateVal <- validateDateParamValue(request.request.getQueryString(toDateParamKey))
      _ <- validateToDateIsAfterFromDate(fromDateVal, toDateVal)
    } yield NotificationQueryParams(statusVal, fromDateVal, toDateVal)
  }

  private def validateStatusParamValue(maybeStatusStr: Option[String]): Either[Result, Option[NotificationStatus]] = {
    maybeStatusStr match {
      case Some(status) => NotificationStatus.apply(status).toRight[Result] {
          logger.info(s"Invalid Status Param provided: $status")
          BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Invalid Status parameter provided"))
        }.map(Some(_))
      case None         => Right(None)
    }
  }

  def validateDateParamValue(maybeString: Option[String]): Either[Result, Option[Instant]] = {
    maybeString match {
      case Some(stringVal) =>
        Try[Instant] {
          lenientFormatter.parse(stringVal, inst => Instant.from(inst))
        } match {
          case Success(parseDate) => Right(Some(parseDate))
          case Failure(_)         =>
            logger.info(s"Unparsable DateValue Param provided: $stringVal")
            Left(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Unparsable DateValue Param provided")))
        }
      case None            => Right(None)
    }
  }

  def validateToDateIsAfterFromDate(fromDateVal: Option[Instant], toDateVal: Option[Instant]): Either[Result, Option[Boolean]] = {
    (fromDateVal, toDateVal) match {
      case (Some(fromDate), Some(toDate)) =>
        if (fromDate.isBefore(toDate)) {
          Right(Some(true))
        } else {
          Left(BadRequest(JsErrorResponse(ErrorCode.BAD_REQUEST, "fromDate parameter is before toDateParameter")))
        }
      case _                              => Right(Some(true))
    }
  }

}

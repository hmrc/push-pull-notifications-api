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

package uk.gov.hmrc.pushpullnotificationsapi.connectors

import java.net.URL
import java.util.regex.Pattern
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status.{BAD_GATEWAY, GATEWAY_TIMEOUT, INTERNAL_SERVER_ERROR}
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}

import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig
import uk.gov.hmrc.pushpullnotificationsapi.models.CallbackValidation
import uk.gov.hmrc.pushpullnotificationsapi.models.notifications.OutboundNotification
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

@Singleton
class OutboundProxyConnector @Inject() (appConfig: AppConfig, httpClient: HttpClientV2)(implicit ec: ExecutionContext)
    extends ApplicationLogger {

  import OutboundProxyConnector._

  def addProxyIfRequired(requestBuilder: RequestBuilder): RequestBuilder = if (appConfig.useProxy) {
    requestBuilder.withProxy
  } else {
    requestBuilder
  }

  private val destinationUrlPattern: Pattern = "^https.*".r.pattern

  private val validate: String => Try[String] =
    OutboundProxyConnector.validateDestinationUrl(appConfig.validateCallbackUrlIsHttps, destinationUrlPattern, appConfig.allowedHostList) _

  def postNotification(notification: OutboundNotification): Future[Int] = {

    def failWith(statusCode: Int): Int = {
      def message = s"Attempted request to ${notification.destinationUrl} responded with HTTP response code $statusCode"
      logger.warn(message)
      statusCode
    }

    def failWithThrowable(t: Throwable): Int = {
      val message: String = s"Attempted request to ${notification.destinationUrl} responded caused ${t.getMessage()}"
      logger.warn(message)
      INTERNAL_SERVER_ERROR
    }

    implicit val irrelevantHc: HeaderCarrier = HeaderCarrier()

    Future.fromTry(validate(notification.destinationUrl))
      .flatMap { url =>
        val extraHeaders = (CONTENT_TYPE -> "application/json") :: notification.forwardedHeaders.map(fh => (fh.key, fh.value))

        addProxyIfRequired(httpClient.post(url"$url"))
          .withBody(Json.parse(notification.payload))
          .setHeader(extraHeaders: _*)
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
          .map {
            case Left(UpstreamErrorResponse(_, statusCode, _, _)) => failWith(statusCode)
            case Right(r: HttpResponse)                           => r.status
          }
          .recover {
            case _: GatewayTimeoutException => failWith(GATEWAY_TIMEOUT)
            case _: BadGatewayException     => failWith(BAD_GATEWAY)
            case NonFatal(e)                => failWithThrowable(e)
          }
      }
  }

  def validateCallback(callbackValidation: CallbackValidation, challenge: String): Future[String] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    Future.fromTry(validate(callbackValidation.callbackUrl))
      .flatMap { validatedCallbackUrl =>
        addProxyIfRequired(httpClient.get(url"$validatedCallbackUrl?challenge=$challenge"))
          .execute[CallbackValidationResponse]
          .map(_.challenge)
      }
  }
}

object OutboundProxyConnector extends ApplicationLogger {
  implicit val callbackValidationResponseFormat: OFormat[CallbackValidationResponse] = Json.format[CallbackValidationResponse]
  private[connectors] case class CallbackValidationResponse(challenge: String)

  def validateDestinationUrl(validateCallbackUrlIsHttps: Boolean, destinationUrlPattern: Pattern, allowedHostList: List[String])(destinationUrl: String): Try[String] = {
    val optionalPattern = Some(destinationUrlPattern).filter(_ => validateCallbackUrlIsHttps)
    (
      for {
        _ <- validateUrlProtocol(optionalPattern)(destinationUrl)
        _ <- validateAgainstAllowedHostList(allowedHostList)(destinationUrl)
      } yield destinationUrl
    )
      .fold(
        err => Failure(new IllegalArgumentException(err)),
        ok => Success(ok)
      )
  }

  private def validateUrlProtocol(destinationUrlPattern: Option[Pattern])(destinationUrl: String): Either[String, String] = {
    destinationUrlPattern match {
      case None          => Right(destinationUrl)
      case Some(pattern) =>
        if (pattern.matcher(destinationUrl).matches()) {
          Right(destinationUrl)
        } else {
          logger.error(s"Invalid destination URL $destinationUrl")
          Left(s"Invalid destination URL $destinationUrl")
        }
    }
  }

  private def validateAgainstAllowedHostList(allowedHostList: List[String])(destinationUrl: String): Either[String, String] = {
    if (allowedHostList.nonEmpty) {
      val host = new URL(destinationUrl).getHost
      if (allowedHostList.contains(host)) {
        Right(destinationUrl)
      } else {
        logger.error(s"Invalid host $host")
        Left(s"Invalid host $host")
      }
    } else {
      Right(destinationUrl)
    }
  }

}

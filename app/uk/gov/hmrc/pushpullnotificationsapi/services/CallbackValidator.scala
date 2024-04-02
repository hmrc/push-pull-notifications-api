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

package uk.gov.hmrc.pushpullnotificationsapi.services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import uk.gov.hmrc.http.{HttpException, UpstreamErrorResponse}

import uk.gov.hmrc.pushpullnotificationsapi.connectors.OutboundProxyConnector
import uk.gov.hmrc.pushpullnotificationsapi.models.{CallbackValidation, CallbackValidationResult}
import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

@Singleton
class CallbackValidator @Inject() (outboundProxyConnector: OutboundProxyConnector, challengeGenerator: ChallengeGenerator)(implicit ec: ExecutionContext)
    extends ApplicationLogger {

  def validateCallback(callbackValidation: CallbackValidation): Future[CallbackValidationResult] = {
    def failedRequestLogMessage(statusCode: Int) = s"Attempted validation of URL ${callbackValidation.callbackUrl} responded with HTTP response code $statusCode"

    val challenge = challengeGenerator.generateChallenge
    outboundProxyConnector.validateCallback(callbackValidation, challenge) map { returnedChallenge =>
      if (returnedChallenge == challenge) {
        CallbackValidationResult(successful = true)
      } else {
        CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
      }
    } recover {
      case httpException: HttpException                 =>
        logger.warn(failedRequestLogMessage(httpException.responseCode))
        CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
      case upstreamErrorResponse: UpstreamErrorResponse =>
        logger.warn(failedRequestLogMessage(upstreamErrorResponse.statusCode))
        CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
      case NonFatal(e)                                  =>
        logger.warn(s"Attempted validation of URL ${callbackValidation.callbackUrl} failed with error ${e.getMessage}")
        CallbackValidationResult(successful = false, Some("Invalid callback URL. Check the information you have provided is correct."))
    }
  }
}

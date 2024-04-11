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

package uk.gov.hmrc.pushpullnotificationsapi.services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.pushpullnotificationsapi.models.{CallbackValidation, PushServiceFailedResult, PushServiceResult, PushServiceSuccessResult, UpdateCallbackUrlRequest}

@Singleton()
class PushService @Inject() (callbackValidator: CallbackValidator)(implicit ec: ExecutionContext) {

  def validateCallbackUrl(request: UpdateCallbackUrlRequest): Future[PushServiceResult] = {
    callbackValidator.validateCallback(CallbackValidation(request.callbackUrl)) map {
      result =>
        if (result.successful) PushServiceSuccessResult()
        else result.errorMessage.fold(PushServiceFailedResult("Unknown Error"))(PushServiceFailedResult)
    }
  }
}

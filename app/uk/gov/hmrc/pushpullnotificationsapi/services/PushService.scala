package uk.gov.hmrc.pushpullnotificationsapi.services

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.pushpullnotificationsapi.models.UpdateCallbackUrlRequest
import scala.concurrent.Future
import uk.gov.hmrc.pushpullnotificationsapi.models.PushConnectorResult
import uk.gov.hmrc.pushpullnotificationsapi.models.PushConnectorSuccessResult
import uk.gov.hmrc.pushpullnotificationsapi.models.CallbackValidation
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.pushpullnotificationsapi.models.PushConnectorFailedResult

@Singleton()
class PushService @Inject() (callbackValidator: CallbackValidator)(implicit ec: ExecutionContext) {
  def validateCallbackUrl(request: UpdateCallbackUrlRequest): Future[PushConnectorResult] = {
    callbackValidator.validateCallback(CallbackValidation(request.callbackUrl)) map { result => if (result.successful) PushConnectorSuccessResult()
        else result.errorMessage.fold(PushConnectorFailedResult("Unknown Error"))(PushConnectorFailedResult)}
  }
}

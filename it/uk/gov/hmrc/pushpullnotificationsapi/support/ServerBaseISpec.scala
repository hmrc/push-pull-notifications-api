package uk.gov.hmrc.pushpullnotificationsapi.support

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import play.api.Application
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

abstract class ServerBaseISpec
  extends BaseISpec with ScalaFutures with DefaultAwaitTimeout with FutureAwaits {


  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(4, Seconds), interval = Span(1, Seconds))

}

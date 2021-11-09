package uk.gov.hmrc.pushpullnotificationsapi.support

import akka.stream.Materializer
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerTest
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import java.util.regex.Pattern
import scala.concurrent.Future

abstract class BaseISpec
  extends AnyWordSpec with Matchers with GuiceOneServerPerTest with WireMockSupport  {

  protected def appBuilder: GuiceApplicationBuilder


  protected implicit def materializer: Materializer = app.materializer

  protected def checkHtmlResultWithBodyText(result: Future[Result], expectedSubstring: String): Unit = {
    status(result) mustBe 200
    contentType(result) mustBe Some("text/html")
    charset(result) mustBe Some("utf-8")
    contentAsString(result) must include(expectedSubstring)
  }

  private lazy val messagesApi = app.injector.instanceOf[MessagesApi]
  private implicit def messages: Messages = messagesApi.preferred(Seq.empty[Lang])

  protected def htmlEscapedMessage(key: String): String = HtmlFormat.escape(Messages(key)).toString

  implicit def hc(implicit request: FakeRequest[_]): HeaderCarrier =
    HeaderCarrierConverter.fromRequestAndSession(request, request.session)

  val uuidPattern: Pattern = Pattern.compile("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
  def validateStringIsUUID(toTest: String): Unit ={
    uuidPattern.matcher(toTest).find() mustBe true
  }
}

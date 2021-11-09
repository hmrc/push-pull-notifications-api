package uk.gov.hmrc.pushpullnotificationsapi.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, TestData}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerTest
import play.api.{Application, Mode}
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.Helpers.{charset, contentAsString, contentType, status}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits, RunningServer}
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import java.util.regex.Pattern
import scala.concurrent.Future

trait NewBaseISpec extends AnyWordSpec with  Matchers with GuiceOneServerPerTest
with FutureAwaits with DefaultAwaitTimeout with BeforeAndAfterAll with BeforeAndAfterEach {


  val stubPort = 11111
  val stubHost = "localhost"

  override protected def newServerForTest(app: Application, testData: TestData): RunningServer = MyTestServerFactory.start(app)


  val wireMockServer = new WireMockServer(wireMockConfig()
    .port(stubPort))

  override def newAppForTest(testData: TestData): Application = {
    GuiceApplicationBuilder()
      .configure(
        "run.mode" -> "Stub",
        "microservice.services.auth.port" -> stubPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> false,
        "auditing.consumer.baseUri.host" -> stubHost,
        "auditing.consumer.baseUri.port" -> stubPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "microservice.services.push-pull-notifications-gateway.port" -> stubPort,
        "microservice.services.push-pull-notifications-gateway.authorizationKey" -> "iampushpullapi",
        "microservice.services.third-party-application.port" -> stubPort
      )
      .in(Mode.Prod)
      .build()
  }

  override def beforeAll() = {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterAll() = {
    wireMockServer.stop()
  }

  override def beforeEach() = {
    WireMock.reset()
  }


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

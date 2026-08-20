import play.core.PlayVersion
import sbt.*
import play.sbt.PlayImport.*

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies
  private val bootstrapVersion = "10.8.0"
  private val mongoVersion = "2.13.0"
  private val mockitoScalaVersion = "2.2.1"

  private val commonDomainVersion    = "1.4.0"
  private val appEventVersion        = "1.3.0"
  private val appDomainVersion       = "1.6.0"


  lazy val dependencies = Seq(
    ws,
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"        % bootstrapVersion,
    "commons-codec"           %  "commons-codec"                    % "1.15",
    "uk.gov.hmrc"             %% "domain-play-30"                   % "10.0.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-30"               % mongoVersion,
    "uk.gov.hmrc"             %% "api-platform-common-domain"       % commonDomainVersion,
    "uk.gov.hmrc"             %% "api-platform-application-events"  % appEventVersion,
    "uk.gov.hmrc"             %% "api-platform-application-domain"  % appDomainVersion,
    "com.github.blemale"      %% "scaffeine"                        % "5.2.1",
    "com.lihaoyi"             %% "sourcecode"                       % "0.3.0",
    "uk.gov.hmrc"             %% "crypto-json-play-30"              % "8.4.0",
    "org.typelevel"           %% "cats-core"                        % "2.10.0"
  )

  lazy val testDependencies = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"                    % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"                   % mongoVersion,
    "org.mockito"             %% "mockito-scala-scalatest"                   % mockitoScalaVersion,
    "org.playframework"       %% "play-pekko-http-server"                    % "3.0.1",
    "uk.gov.hmrc"             %% "api-platform-common-domain-fixtures"       % commonDomainVersion,
    "uk.gov.hmrc"             %% "api-platform-application-domain-fixtures"  % appDomainVersion
  ).map(_ % "test")
}

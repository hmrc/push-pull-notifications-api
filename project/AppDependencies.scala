import play.core.PlayVersion
import sbt._
import play.sbt.PlayImport._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies
  val bootstrapVersion = "9.18.0"
  val mongoVersion = "2.7.0"
  val eventsVersion = "0.83.0"
  val commonDomainVersion = "0.18.0"
  val appDomainVersion = "0.82.0"
  
  lazy val dependencies = Seq(
    ws,
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"        % bootstrapVersion,
    "commons-codec"           %  "commons-codec"                    % "1.15",
    "uk.gov.hmrc"             %% "domain-play-30"                   % "10.0.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-30"               % mongoVersion,
    "uk.gov.hmrc"             %% "api-platform-application-events"  % eventsVersion,
    "com.github.blemale"      %% "scaffeine"                        % "5.2.1",
    "com.lihaoyi"             %% "sourcecode"                       % "0.3.0",
    "uk.gov.hmrc"             %% "crypto-json-play-30"              % "8.2.0",
    "org.typelevel"           %% "cats-core"                        % "2.10.0"
  )

  lazy val testDependencies = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"                    % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"                   % mongoVersion,
    "org.mockito"             %% "mockito-scala-scalatest"                   % "1.17.29",
    "org.playframework"       %% "play-pekko-http-server"                    % "3.0.1",
    "uk.gov.hmrc"             %% "api-platform-application-domain-fixtures"  % appDomainVersion
  ).map(_ % "test")
}

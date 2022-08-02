import play.core.PlayVersion
import sbt._
import play.sbt.PlayImport._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies
  lazy val bootstrapVersion = "5.14.0"
  lazy val dependencies = Seq(
    ws,
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  %  bootstrapVersion,
    "uk.gov.hmrc"             %% "crypto"                     % "5.6.0",
    "uk.gov.hmrc"             %% "play-json-union-formatter"  % "1.15.0-play-28",
    "uk.gov.hmrc"             %% "domain"                     % "6.2.0-play-28",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"         % "0.68.0",
    "com.github.blemale"      %% "scaffeine"                  % "3.1.0",
    "com.typesafe.play"       %% "play-json"                  % "2.7.1",
    "com.typesafe.play"       %% "play-json-joda"             % "2.7.1",
    "com.beachape"            %% "enumeratum-play-json"       % "1.6.0"
  )

  lazy val testDependencies = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVersion,
    "org.mockito"             %% "mockito-scala-scalatest"    % "1.14.8",
    "com.typesafe.play"       %% "play-akka-http-server"      % "2.8.7",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % "0.68.0",
    "com.github.tomakehurst"  %  "wiremock-jre8-standalone"   % "2.27.2"
  ).map(_ % "test, it")
}

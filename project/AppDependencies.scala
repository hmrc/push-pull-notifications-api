import play.core.PlayVersion
import sbt._
import play.sbt.PlayImport._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  lazy val dependencies = Seq(
    ws,
    "uk.gov.hmrc"             %% "bootstrap-backend-play-26"  % "5.10.0",
    "uk.gov.hmrc"             %% "crypto"                     % "5.6.0",
    "uk.gov.hmrc"             %% "play-json-union-formatter"  % "1.14.0-play-26",
    "uk.gov.hmrc"             %% "domain"                     % "5.10.0-play-26",
    "uk.gov.hmrc"             %% "agent-kenshoo-monitoring"   % "4.7.0-play-26",
    "uk.gov.hmrc"             %% "simple-reactivemongo"       % "7.30.0-play-26",
    "uk.gov.hmrc"             %% "play-scheduling"            % "7.4.0-play-26",
    "com.kenshoo"             %% "metrics-play"               % "2.6.19_0.7.0",
    "org.reactivemongo"       %% "reactivemongo-akkastream"   % "0.20.13",
    "com.github.blemale"      %% "scaffeine"                  % "3.1.0",
    "com.typesafe.play"       %% "play-json"                  % "2.7.1",
    "com.typesafe.play"       %% "play-json-joda"             % "2.7.1",
    "com.beachape"            %% "enumeratum-play-json"       % "1.6.0"
  )

  lazy val testDependencies = Seq(
    "org.mockito"             %% "mockito-scala-scalatest"    % "1.14.8",
    "org.scalatestplus.play"  %% "scalatestplus-play"         % "3.1.3",
    "uk.gov.hmrc"             %% "reactivemongo-test"         % "4.21.0-play-26",
    "com.github.tomakehurst"  %  "wiremock-jre8-standalone"   % "2.27.2"
  ).map(_ % "test, it")
}

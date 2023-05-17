import play.core.PlayVersion
import sbt._
import play.sbt.PlayImport._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies
  lazy val bootstrapVersion = "7.15.0"
  lazy val mongoVersion = "0.73.0"
  lazy val dependencies = Seq(
    ws,
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % bootstrapVersion,
    "uk.gov.hmrc"             %% "play-json-union-formatter"  % "1.18.0-play-28",
    "commons-codec"           %  "commons-codec"              % "1.15",
    "uk.gov.hmrc"             %% "domain"                     % "8.3.0-play-28",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"         % mongoVersion,
    "com.github.blemale"      %% "scaffeine"                  % "5.2.1",
    "com.typesafe.play"       %% "play-json"                  % "2.9.3",
    "com.typesafe.play"       %% "play-json-joda"             % "2.9.3",
    "com.beachape"            %% "enumeratum-play-json"       % "1.6.0",
    "com.lihaoyi"             %% "sourcecode"                 % "0.3.0"
  )

  lazy val testDependencies = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVersion,
    "org.mockito"             %% "mockito-scala-scalatest"    % "1.16.42",
    "com.typesafe.play"       %% "play-akka-http-server"      % "2.8.7",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % mongoVersion,
    "com.github.tomakehurst"  %  "wiremock-jre8-standalone"   % "2.33.2",
    "org.scalatestplus.play"  %% "scalatestplus-play"         % "5.1.0"
  ).map(_ % "test, it")
}

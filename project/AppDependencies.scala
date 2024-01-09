import play.core.PlayVersion
import sbt._
import play.sbt.PlayImport._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies
  lazy val bootstrapVersion = "7.22.0"
  lazy val mongoVersion = "0.74.0"
  private val commonDomainVersion = "0.10.0"
  
  lazy val dependencies = Seq(
    ws,
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"        % bootstrapVersion,
    "commons-codec"           %  "commons-codec"                    % "1.15",
    "uk.gov.hmrc"             %% "domain"                           % "8.0.0-play-28",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"               % mongoVersion,
    "uk.gov.hmrc"             %% "api-platform-application-events"  % "0.38.0",
    "com.github.blemale"      %% "scaffeine"                        % "5.2.1",
    "com.beachape"            %% "enumeratum-play-json"             % "1.6.0",
    "com.lihaoyi"             %% "sourcecode"                       % "0.3.0",
    "org.typelevel"           %% "cats-core"                        % "2.9.0"
  )

  lazy val testDependencies = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"           % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"          % mongoVersion,
    "org.mockito"             %% "mockito-scala-scalatest"          % "1.17.29",
    "org.scalatest"           %% "scalatest"                        % "3.2.17",
    "com.vladsch.flexmark"    % "flexmark-all"                      % "0.62.2",
    "com.typesafe.play"       %% "play-akka-http-server"            % "2.8.7",
    "com.github.tomakehurst"  %  "wiremock-jre8-standalone"         % "2.27.2",
    "uk.gov.hmrc"             %% "api-platform-test-common-domain"  % commonDomainVersion
  ).map(_ % "test, it")
}

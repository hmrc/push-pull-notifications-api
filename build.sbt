import sbt.Tests.{Group, SubProcess}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.SbtAutoBuildPlugin

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimum := 86,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val compileDeps = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-play-26" % "1.3.0",
  "uk.gov.hmrc" %% "play-json-union-formatter" % "1.10.0-play-26",
  "com.kenshoo" %% "metrics-play" % "2.6.19_0.7.0",
  "uk.gov.hmrc" %% "domain" % "5.6.0-play-26",
  "com.github.blemale" %% "scaffeine" % "3.1.0",
  "uk.gov.hmrc" %% "agent-kenshoo-monitoring" % "4.0.0",
  "uk.gov.hmrc" %% "simple-reactivemongo" % "7.22.0-play-26",
  "com.typesafe.play" %% "play-json-joda" % "2.6.10",
  ws
)

def testDeps(scope: String) = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % scope,
  "org.scalatest" %% "scalatest" % "3.0.8" % scope,
  "org.mockito" %% "mockito-scala-scalatest" % "1.9.0" % scope,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % scope,
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.16.0-play-26" % scope,
  "com.github.tomakehurst" % "wiremock" % "2.25.1" % scope
)

val jettyVersion = "9.2.24.v20180105"

val jettyOverrides = Seq(
  "org.eclipse.jetty" % "jetty-server" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-servlet" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-security" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-servlets" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-continuation" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-webapp" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-xml" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-client" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-http" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-io" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty" % "jetty-util" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty.websocket" % "websocket-api" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty.websocket" % "websocket-common" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty.websocket" % "websocket-client" % jettyVersion % IntegrationTest
)

lazy val root = (project in file("."))
  .settings(
    name := "push-pull-notifications-api",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.12.10",
    PlayKeys.playDefaultPort := 6701,
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.bintrayRepo("hmrc", "release-candidates"),
      Resolver.typesafeRepo("releases"),
      Resolver.jcenterRepo
    ),
    libraryDependencies ++= compileDeps ++ testDeps("test") ++ testDeps("it"),
    dependencyOverrides ++= jettyOverrides,
    publishingSettings,
    scoverageSettings,
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources"
  )
  .configs(IntegrationTest)
  .settings(
    Keys.fork in IntegrationTest := false,
    Defaults.itSettings,
    unmanagedSourceDirectories in IntegrationTest += baseDirectory(_ / "it").value,
    parallelExecution in IntegrationTest := false,
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    majorVersion := 0
  )
  .settings(scalacOptions ++= Seq("-deprecation", "-feature", "-Ypartial-unification"))
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)

def oneForkedJvmPerTest(tests: Seq[TestDefinition]) = {
  tests.map { test =>
    new Group(test.name, Seq(test), SubProcess(ForkOptions().withRunJVMOptions(Vector(s"-Dtest.name=${test.name}"))))
  }
}
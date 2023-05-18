import sbt.Tests._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.SbtAutoBuildPlugin
import bloop.integrations.sbt.BloopDefaults
import sbt.Keys._

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"

inThisBuild(
  List(
    scalaVersion := "2.12.15",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimumStmtTotal := 95.1,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val root = (project in file("."))
  .disablePlugins(JUnitXmlReportPlugin)
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
  .settings(
    name := "push-pull-notifications-api",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.12.15",
    scalacOptions += "-Ypartial-unification",
    majorVersion := 0,
    PlayKeys.playDefaultPort := 6701,
    resolvers += Resolver.typesafeRepo("releases"),
    libraryDependencies ++= AppDependencies(),
    scoverageSettings,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources"
  )
  .configs(IntegrationTest)
  .settings(inConfig(Test)(BloopDefaults.configSettings))
  .settings(inConfig(IntegrationTest)(BloopDefaults.configSettings))
  .settings(
    Defaults.itSettings,
    IntegrationTest / fork := false,
    IntegrationTest / unmanagedSourceDirectories += baseDirectory.value / "it",
    IntegrationTest / unmanagedSourceDirectories += baseDirectory.value / "testcommon",
    IntegrationTest / parallelExecution := false,
    IntegrationTest / testGrouping := oneForkedJvmPerTest((IntegrationTest / definedTests).value),
    IntegrationTest / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-eT"),

    Test / unmanagedSourceDirectories += baseDirectory.value / "testcommon",
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-eT")
  )
  .settings(
    routesImport ++= Seq(
      "uk.gov.hmrc.pushpullnotificationsapi.models._",
      "uk.gov.hmrc.pushpullnotificationsapi.controllers.Binders._",
      "uk.gov.hmrc.apiplatform.modules.applications.domain.models._"
    )
  )
  .settings(
    scalacOptions ++= Seq("-deprecation", "-feature", "-Ypartial-unification")
  )

def oneForkedJvmPerTest(tests: Seq[TestDefinition]) = {
  tests.map { test =>
    new Group(test.name, Seq(test), SubProcess(ForkOptions().withRunJVMOptions(Vector(s"-Dtest.name=${test.name}"))))
  }
}

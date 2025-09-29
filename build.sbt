import uk.gov.hmrc.DefaultBuildSettings

lazy val appName = "push-pull-notifications-api"

Global / bloopAggregateSourceDependencies := true
Global / bloopExportJarClassifiers := Some(Set("sources"))

ThisBuild / scalaVersion := "2.13.16"
ThisBuild / majorVersion := 0
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(CodeCoverageSettings.settings)
  .settings(
    libraryDependencies ++= AppDependencies(),
    PlayKeys.playDefaultPort := 6701,
    retrieveManaged := true
  )
  .settings(
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Test / unmanagedSourceDirectories += baseDirectory.value / "testcommon",
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-eT"),
    Test / javaOptions += s"-javaagent:${csrCacheDirectory.value.getAbsolutePath}/https/repo1.maven.org/maven2/org/mockito/mockito-core/${AppDependencies.mockitoVersion}/mockito-core-${AppDependencies.mockitoVersion}.jar"
  )
  .settings(
    routesImport ++= Seq(
      "uk.gov.hmrc.pushpullnotificationsapi.models._",
      "uk.gov.hmrc.pushpullnotificationsapi.controllers.Binders._",
      "uk.gov.hmrc.apiplatform.modules.common.domain.models._"
    )
  )
  .settings(
    scalacOptions ++= Seq(
      "-Xlint:-missing-interpolator,_",
      // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
      // suppress warnings in generated routes files
      "-Wconf:src=routes/.*:s"
    )
  )

lazy val it = (project in file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(
    name := "integration-tests",
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-eT"),
    DefaultBuildSettings.itSettings()
  )

commands ++= Seq(
  Command.command("cleanAll") { state => "clean" :: "it/clean" :: state },
  Command.command("fmtAll") { state => "scalafmtAll" :: "it/scalafmtAll" :: state },
  Command.command("fixAll") { state => "scalafixAll" :: "it/scalafixAll" :: state },
  Command.command("testAll") { state => "test" :: "it/test" :: state },

  Command.command("run-all-tests") { state => "testAll" :: state },
  Command.command("clean-and-test") { state => "cleanAll" :: "compile" :: "run-all-tests" :: state },
  Command.command("pre-commit") { state => "cleanAll" :: "fmtAll" :: "fixAll" :: "coverage" :: "testAll" :: "coverageOff" :: "coverageAggregate" :: state }
)

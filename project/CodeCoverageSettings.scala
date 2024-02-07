import sbt.Keys.parallelExecution
import sbt.{Setting, Test}
import scoverage.ScoverageKeys

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    "uk.gov.hmrc.BuildInfo",
    ".*Routes.*",
    ".*RoutesPrefix.*",
    ".*Filters?",
    "MicroserviceAuditConnector",
    "Module",
    "GraphiteStartUp",
    ".*\\.Reverse[^.]*"
  )

  val settings: Seq[Setting[_]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 95.1,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}

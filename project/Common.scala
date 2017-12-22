import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.mimaPreviousArtifacts

object Common extends AutoPlugin {
  import com.typesafe.tools.mima.core._

  override def trigger = allRequirements
  override def requires = JvmPlugin

  val previousVersion = "2.5.3"

  override def projectSettings = mimaDefaultSettings ++ Seq(
    organization := "org.playframework.anorm",
    resolvers += "Scalaz Bintray Repo" at {
      "http://dl.bintray.com/scalaz/releases" // specs2 depends on scalaz-stream
    },
    scalacOptions ~= (_.filterNot(_ == "-Xfatal-warnings")),
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xlint",
      "-Ywarn-unused-import", "-Ywarn-unused", "-Ywarn-dead-code",
      "-Ywarn-numeric-widen"),
    fork in Test := true,
    mimaPreviousArtifacts := {
      if (scalaVersion.value startsWith "2.12.") Set.empty else {
        if (crossPaths.value) {
          Set("com.typesafe.play" % s"${moduleName.value}_${scalaBinaryVersion.value}" % previousVersion)
        } else {
          Set("com.typesafe.play" % moduleName.value % previousVersion)
        }
      }
    }) ++ Publish.settings

  @inline def missMeth(n: String) =
    ProblemFilters.exclude[MissingMethodProblem](n)

  @inline def incoMeth(n: String) =
    ProblemFilters.exclude[IncompatibleMethTypeProblem](n)

  @inline def incoRet(n: String) =
    ProblemFilters.exclude[IncompatibleResultTypeProblem](n)

}

object AnormGeneration {
  def generateFunctionAdapter(dir: File): File = {
    val out = dir / "FunctionAdapter.scala"

    if (out exists) out else {
      IO.writer[File](out, "", IO.defaultCharset, false) { w ⇒
        w.append("""package anorm

/** Function adapters to work with extract data. */
private[anorm] trait FunctionAdapter {
  /**
   * Returns function application with a single column.
   * @tparam T1 the type of column
   * @tparam R the type of result from `f` applied with the column
   * @param f the function applied with column
   */
  def to[T1, R](f: Function1[T1, R]): T1 => R = f""")

        (2 to 22).foreach { i =>
          val values = (1 to i).map(j => s"c$j")
          val types = (1 to i).map(j => s"T$j")

          w.append(s"""

  /**
   * Returns function applicable with a $i-column tuple-like.""")

          (1 to i).foreach { j =>
            w.append(s"""
   * @tparam T$j the type of column #$j""")
          }

          w.append("""
   * @tparam R the type of result from `f` applied with the columns
   * @param f the function applied with columns
   */
  def to[""")

          (1 to i).foreach { j => w.append(s"T$j, ") }

          w.append(s"R](f: Function$i[${types mkString ", "}, R]): ${types mkString " ~ "} => R = { case ${values mkString " ~ "} => f(${values mkString ", "}) }")
        }

        w.append("""
}""")

        out
      }
    }
  }
}

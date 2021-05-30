import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

import com.typesafe.tools.mima.plugin.MimaKeys.mimaPreviousArtifacts

object Common extends AutoPlugin {
  import com.typesafe.tools.mima.core._

  override def trigger = allRequirements
  override def requires = JvmPlugin

  val previousVersion = "2.6.0"

  override def projectSettings = Seq(
    organization := "org.playframework.anorm",
    scalaVersion := "2.12.13",
    crossScalaVersions := Seq(
      "2.11.12", scalaVersion.value, "2.13.3"),
    resolvers += "Scalaz Bintray Repo" at {
      "https://dl.bintray.com/scalaz/releases" // specs2 depends on scalaz-stream
    },
    (Compile / unmanagedSourceDirectories) ++= {
      val sv = scalaVersion.value

      Seq(
        scala2Unmanaged(sv, 12, (Compile / sourceDirectory).value),
        scala2Unmanaged(sv, 13, (Compile / sourceDirectory).value))
    },
    (Test / unmanagedSourceDirectories) += scala2Unmanaged(
      scalaVersion.value, 12,
      (Test / sourceDirectory).value),
    libraryDependencies ++= {
      val silencerVer = "1.7.5"

      Seq(
        compilerPlugin(("com.github.ghik" %% "silencer-plugin" % silencerVer).
          cross(CrossVersion.full)),
        ("com.github.ghik" %% "silencer-lib" % silencerVer % Provided).
          cross(CrossVersion.full)
      )
    },
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-target:jvm-1.8",
      "-unchecked",
      "-deprecation",
      "-feature",
      "-Xfatal-warnings",
      "-Xlint",
      "-g:vars"
    ),
    scalacOptions ++= {
      if (scalaBinaryVersion.value == "2.12") {
        Seq(
          "-Xmax-classfile-name", "128",
          "-Ywarn-numeric-widen",
          "-Ywarn-dead-code",
          "-Ywarn-value-discard",
          "-Ywarn-infer-any",
          "-Ywarn-unused",
          "-Ywarn-unused-import",
          "-Ywarn-macros:after"
        )
      } else if (scalaBinaryVersion.value == "2.11") {
        Seq(
          "-Xmax-classfile-name", "128",
          "-Yopt:_", "-Ydead-code", "-Yclosure-elim", "-Yconst-opt")
      } else {
        Seq(
          "-explaintypes",
          "-Werror",
          "-Wnumeric-widen",
          "-Wdead-code",
          "-Wvalue-discard",
          "-Wextra-implicit",
          "-Wunused",
          "-Wmacros:after")
      }
    },
    Compile / console / scalacOptions ~= {
      _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
    },
    Test / console / scalacOptions ~= {
      _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
    },
    Test / scalacOptions ++= Seq("-Yrangepos"),
    Test / scalacOptions ~= (_.filterNot(_ == "-Werror")),
    scalacOptions ~= (_.filterNot(_ == "-Xfatal-warnings")),
    Test / fork := true,
    mimaPreviousArtifacts := Set(
      organization.value %% moduleName.value % previousVersion)
  ) ++ Publish.settings

  @inline def missMeth(n: String) =
    ProblemFilters.exclude[MissingMethodProblem](n)

  @inline def incoMeth(n: String) =
    ProblemFilters.exclude[IncompatibleMethTypeProblem](n)

  @inline def incoRet(n: String) =
    ProblemFilters.exclude[IncompatibleResultTypeProblem](n)

  def scala2Unmanaged(ver: String, minor: Int, base: File): File = 
    CrossVersion.partialVersion(ver) match {
      case Some((2, n)) if n >= minor => base / s"scala-2.${minor}+"
      case _                          => base / s"scala-2.${minor}-"
    }

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

/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import com.typesafe.tools.mima.plugin.MimaKeys.mimaPreviousArtifacts

object Common extends AutoPlugin {
  import com.typesafe.tools.mima.core._

  override def trigger  = allRequirements
  override def requires = JvmPlugin

  val previousVersion: Option[String] = Some("2.6.10")

  override def projectSettings = Seq(
    organization       := "org.playframework.anorm",
    scalaVersion       := "2.12.20",
    crossScalaVersions := Seq("2.11.12", scalaVersion.value, "2.13.16", "3.3.6"),
    Compile / unmanagedSourceDirectories ++= {
      val sv = scalaVersion.value

      scalaUnmanaged(sv, (Compile / sourceDirectory).value)
    },
    Test / unmanagedSourceDirectories ++= scalaUnmanaged(scalaVersion.value, (Test / sourceDirectory).value),
    ThisBuild / libraryDependencies ++= {
      if (scalaBinaryVersion.value != "3") {
        val silencerVersion = "1.7.19"

        Seq(
          compilerPlugin(
            ("com.github.ghik" %% "silencer-plugin" % silencerVersion)
              .cross(CrossVersion.full)
          ),
          ("com.github.ghik" %% "silencer-lib" % silencerVersion % Provided)
            .cross(CrossVersion.full)
        )
      } else Seq.empty
    },
    scalacOptions ++= Seq("-Xfatal-warnings"),
    scalacOptions ++= {
      val v = scalaBinaryVersion.value

      if (v == "2.13") {
        Seq("-release", "8") :+ "-Xlint"
      } else if (v == "3") {
        Seq("-release", "8")
      } else {
        Seq("-target:jvm-1.8", "-g:vars") :+ "-Xlint"
      }
    },
    scalacOptions ++= {
      val v = scalaBinaryVersion.value

      if (v == "3") {
        Seq(
          "-explaintypes",
          "-Werror",
          "-Wconf:msg=.*with\\ as\\ a\\ type\\ operator.*:s",
          "-Wconf:msg=.*deprecated.*uninitialized.*:s",
          "-Wconf:msg=.*vararg\\ splices.*:s",
          "-Wconf:cat=deprecation&msg=.*(reflectiveSelectableFromLangReflectiveCalls|DeprecatedSqlParser|missing .*ToSql|deprecatedName).*:s"
        )
      } else if (v == "2.12") {
        Seq(
          "-Xmax-classfile-name",
          "128",
          "-Ywarn-numeric-widen",
          "-Ywarn-dead-code",
          "-Ywarn-value-discard",
          "-Ywarn-infer-any",
          "-Ywarn-unused",
          "-Ywarn-unused-import",
          "-Ywarn-macros:after"
        )
      } else if (v == "2.11") {
        Seq("-Xmax-classfile-name", "128", "-Yopt:_", "-Ydead-code", "-Yclosure-elim", "-Yconst-opt")
      } else {
        // 2.13
        Seq(
          "-explaintypes",
          "-Werror",
          "-Wunused",
          "-Wnumeric-widen",
          "-Wdead-code",
          "-Wvalue-discard",
          "-Wextra-implicit",
          "-Wmacros:after",
          "-Wconf:msg=.*type\\ Seq\\ in\\ package\\ scala\\ has\\ changed\\ semantics.*:s",
          "-Wconf:cat=deprecation&msg=.*(reflectiveSelectableFromLangReflectiveCalls|DeprecatedSqlParser|missing .*ToSql|deprecatedName).*:s"
        )
      }
    },
    Compile / console / scalacOptions ~= {
      _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
    },
    Test / console / scalacOptions ~= {
      _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
    },
    Test / scalacOptions ++= {
      if (scalaBinaryVersion.value != "3") Seq("-Yrangepos")
      else Seq("-language:implicitConversions")
    },
    Test / scalacOptions ~= (_.filterNot(_ == "-Werror")),
    scalacOptions ~= (_.filterNot(_ == "-Xfatal-warnings")),
    Test / fork           := true,
    mimaPreviousArtifacts := previousVersion.map(organization.value %% moduleName.value % _).toSet
  ) ++ Publish.settings

  @inline def missMeth(n: String) =
    ProblemFilters.exclude[MissingMethodProblem](n)

  @inline def incoMeth(n: String) =
    ProblemFilters.exclude[IncompatibleMethTypeProblem](n)

  @inline def incoRet(n: String) =
    ProblemFilters.exclude[IncompatibleResultTypeProblem](n)

  def scalaUnmanaged(ver: String, base: File): Seq[File] =
    CrossVersion.partialVersion(ver) match {
      case Some((2, 11)) =>
        Seq(base / "scala-2.12-", base / "scala-2.13-")

      case Some((2, 12)) =>
        Seq(base / "scala-2.12+", base / "scala-2.13-")

      case Some((3, _) | (2, 13)) =>
        Seq(base / "scala-2.12+", base / "scala-2.13+")

      case Some((_, minor)) =>
        Seq(base / s"scala-2.${minor}-")

      case _ =>
        sys.error(s"Unexpected version: $ver")
    }

}

object AnormGeneration {
  def generateFunctionAdapter(dir: File): File = {
    val out = dir / "FunctionAdapter.scala"

    if (out.exists) out
    else {
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
          val types  = (1 to i).map(j => s"T$j")

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

          w.append(s"R](f: Function$i[${types.mkString(", ")}, R]): ${types.mkString(" ~ ")} => R = { case ${values
              .mkString(" ~ ")} => f(${values.mkString(", ")}) }")
        }

        w.append("""
}""")

        out
      }
    }
  }
}

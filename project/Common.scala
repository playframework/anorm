import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.mimaPreviousArtifacts

object Common extends AutoPlugin {
  import com.typesafe.tools.mima.core._

  override def trigger = allRequirements
  override def requires = JvmPlugin

  val previousVersion = "2.6.0"

  override def projectSettings = mimaDefaultSettings ++ Seq(
    organization := "org.playframework.anorm",
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq(
      "2.11.12", scalaVersion.value, "2.13.0"),
    resolvers += "Scalaz Bintray Repo" at {
      "http://dl.bintray.com/scalaz/releases" // specs2 depends on scalaz-stream
    },
    unmanagedSourceDirectories in Compile ++= {
      val sv = scalaVersion.value

      Seq(
        scala2Unmanaged(sv, 12, (sourceDirectory in Compile).value),
        scala2Unmanaged(sv, 13, (sourceDirectory in Compile).value))
    },
    unmanagedSourceDirectories in Test += scala2Unmanaged(
      scalaVersion.value, 12,
      (sourceDirectory in Test).value),
    libraryDependencies ++= {
      val silencerVer = "1.4.2"

      Seq(
        compilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVer),
        "com.github.ghik" %% "silencer-lib" % silencerVer % Provided)
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
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n < 12 => Seq.empty[String]
        case _ => Seq("-Ywarn-macros:after")
      }
    },
    scalacOptions in Compile ++= {
      if ((scalaVersion in Compile).value startsWith "2.12.") {
        Seq(
          "-Ywarn-numeric-widen",
          "-Ywarn-infer-any",
          "-Ywarn-dead-code",
          "-Ywarn-unused",
          "-Ywarn-unused-import",
          "-Ywarn-value-discard")
      } else {
        Seq.empty[String]
      }
    },
    scalacOptions in (Compile, console) ~= {
      _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
    },
    scalacOptions in (Test, console) ~= {
      _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
    },
    scalacOptions in Test ++= Seq("-Yrangepos"),
    scalacOptions ~= (_.filterNot(_ == "-Xfatal-warnings")),
    fork in Test := true,
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

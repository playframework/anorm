import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import com.typesafe.sbt.SbtScalariform._
import sbtrelease.ReleasePlugin._
import com.typesafe.sbt.pgp.PgpKeys
import interplay.Omnidoc.Import._
import xerial.sbt.Sonatype

object Common extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin

  override def projectSettings = Seq(
    organization := "com.typesafe.play",

    scalaVersion := sys.props.get("scala.version").getOrElse("2.10.4"),
    crossScalaVersions := Seq("2.10.4", "2.11.6"),

    scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),
    (javacOptions in compile) := Seq("-source", "1.7", "-target", "1.7"),
    (javacOptions in doc) := Seq("-source", "1.7"),

    fork in Test := true,

    resolvers ++= DefaultOptions.resolvers(snapshot = true),
    resolvers += "Scalaz Bintray Repo" at {
      "http://dl.bintray.com/scalaz/releases" // specs2 depends on scalaz-stream
    }
  ) ++ scalariformSettings
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

          w.append("""

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

object Publish extends AutoPlugin {
  override def trigger = noTrigger
  override def requires = interplay.Omnidoc

  override def projectSettings = Release.settings ++ Seq(
    homepage := Some(url("https://github.com/playframework/anorm")),
    licenses := Seq("Apache 2" -> url(
      "http://www.apache.org/licenses/LICENSE-2.0")),
    pomExtra := {
  <scm>
    <url>https://github.com/playframework/anorm</url>
    <connection>scm:git:git@github.com:playframework/anorm.git</connection>
  </scm>
  <developers>
    <developer>
      <id>playframework</id>
      <name>Play Framework Team</name>
      <url>https://github.com/playframework</url>
    </developer>
  </developers>
    },
    pomIncludeRepository := { _ => false },

    OmnidocKeys.githubRepo := "playframework/anorm",
    OmnidocKeys.tagPrefix := ""
  )
}

object NoPublish extends AutoPlugin {
  override def trigger = noTrigger
  override def requires = JvmPlugin

  override def projectSettings = Release.settings ++ Seq(
    publish := (),
    publishLocal := (),
    PgpKeys.publishSigned := (),
    publishTo := Some(Resolver.file("no-publish", crossTarget.value / "no-publish"))
  )
}

object Release {
  import sbtrelease._
  import ReleaseStateTransformations._
  import ReleaseKeys._
  import sbt.complete.Parser

  def settings = releaseSettings ++ Seq(
    ReleaseKeys.crossBuild := true,
    ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value,
    ReleaseKeys.tagName := (version in ThisBuild).value,
    Sonatype.autoImport.sonatypeProfileName := "com.typeasfe",

    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      ReleaseStep(action = { state =>
        Parser.parse("", Sonatype.SonatypeCommand.sonatypeRelease.parser(state)) match {
          case Right(command) => command()
          case Left(msg) => throw sys.error(s"Bad input for release command: $msg")
        }
      }),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )

  )
}

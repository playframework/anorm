import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import com.typesafe.sbt.SbtScalariform._
import sbtrelease.ReleasePlugin._
import com.typesafe.sbt.pgp.PgpKeys
import interplay.Omnidoc.Import._

object Common extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin

  override def projectSettings = Seq(
    organization := "com.typesafe.play",

    scalaVersion := sys.props.get("scala.version").getOrElse("2.10.4"),
    crossScalaVersions := Seq("2.10.4", "2.11.3"),

    scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),
    (javacOptions in compile) := Seq("-source", "1.7", "-target", "1.7"),
    (javacOptions in doc) := Seq("-source", "1.7"),

    fork in Test := true,

    resolvers ++= DefaultOptions.resolvers(snapshot = true),
    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += "Scalaz Bintray Repo" at {
      "http://dl.bintray.com/scalaz/releases" // specs2 depends on scalaz-stream
    }
  )
}

object Publish extends AutoPlugin {
  override def trigger = noTrigger
  override def requires = interplay.Omnidoc

  override def projectSettings = Release.settings ++ Seq(
    publishTo := {
      if (isSnapshot.value) Some(Opts.resolver.sonatypeSnapshots)
      else Some(Opts.resolver.sonatypeStaging)
    },
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
  ) ++ scalariformSettings
}

object NoPublish extends AutoPlugin {
  override def trigger = noTrigger
  override def requires = JvmPlugin

  override def projectSettings = Release.settings ++ Seq(
    publish := (),
    publishLocal := (),
    publishTo := Some(Resolver.file("no-publish", crossTarget.value / "no-publish"))
  )
}

object Release {
  def settings = releaseSettings ++  Seq(
    ReleaseKeys.crossBuild := true,
    ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value,
    ReleaseKeys.tagName := (version in ThisBuild).value
  )
}

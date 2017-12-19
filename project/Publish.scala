import sbt.Keys._
import sbt._

object Publish {
  val siteUrl = "https://playframework.github.io/anorm/"

  lazy val settings = Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    autoAPIMappings := true,
    apiURL := Some(url(s"$siteUrl/anorm/unidoc/anorm/")),
    licenses := {
      Seq("Apache 2.0" ->
        url("http://www.apache.org/licenses/LICENSE-2.0"))
    },
    homepage := Some(url(siteUrl)),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/playframework/anorm"),
        "scm:git://github.com/playframework/anorm.git")),
    developers := List(
      Developer(
        id = "playframework",
        name = "playframework",
        email = "",
        url = url("https://www.playframework.org"))))
}

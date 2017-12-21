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
    homepage := Some(url(siteUrl)))
}

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object Common extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin

  override def projectSettings = Seq(
    organization := "com.typesafe.play",

    scalaVersion := sys.props.get("scala.version").getOrElse("2.10.4"),
    crossScalaVersions := Seq("2.10.4", "2.11.1"),
    libraryDependencies := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, scalaMajor)) if scalaMajor >= 11 =>
          libraryDependencies.value :+ ("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1")
        case _ =>
          libraryDependencies.value
      }
    },

    scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),
    (javacOptions in compile) := Seq("-source", "1.7", "-target", "1.7"),
    (javacOptions in doc) := Seq("-source", "1.7"),

    fork in Test := true,

    resolvers ++= DefaultOptions.resolvers(snapshot = true),
    resolvers += Resolver.typesafeRepo("releases")
  )
}

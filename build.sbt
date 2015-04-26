import scala.util.Properties.isJavaAtLeast
import AnormGeneration.{ generateFunctionAdapter => GFA }

lazy val acolyteVersion =
  if (isJavaAtLeast("1.7")) "1.0.33-j7p" else "1.0.33"

lazy val tokenizer = project
  .in(file("tokenizer"))
  .enablePlugins(Omnidoc, Publish)

lazy val anorm = project
  .in(file("core"))
  .enablePlugins(Playdoc, Omnidoc, Publish)
  .settings({
    sourceGenerators in Compile <+= (
      sourceManaged in Compile).map(m => Seq(GFA(m / "anorm")))
  })
  .settings({
    libraryDependencies ++= Seq(
      "com.jsuereth" %% "scala-arm" % "1.4",
      "joda-time" % "joda-time" % "2.6", // TODO: scope as 'provided' ?
      "org.joda" % "joda-convert" % "1.7",

      "com.h2database" % "h2" % "1.4.182" % Test,
      "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test,
      "com.chuusai" % "shapeless" % "2.0.0" % Test cross CrossVersion.
        binaryMapped {
          case "2.10" => "2.10.4"
          case x => x
        }
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, scalaMajor)) if scalaMajor >= 11 =>
        Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1")
      case _ => Nil
    }) ++ Seq(
      "specs2-core",
      "specs2-junit"
    ).map("org.specs2" %% _ % "2.4.9" % Test)
  }).dependsOn(tokenizer)

lazy val java8 = project
  .in(file("java8"))
  .dependsOn(anorm)
  .enablePlugins(Omnidoc, Publish)
  .settings(javacOptions := Seq("-source", "1.8", "-target", "1.8"))

lazy val root = Project(id = "anorm-parent", base = file("."))
  .enablePlugins(NoPublish)
  .aggregate(tokenizer, anorm) configure { p =>
    if (isJavaAtLeast("1.8")) p.aggregate(java8) else p
  }

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(PlayDocsPlugin)
  .dependsOn(anorm)

name := "root"

lazy val tokenizer = project.in(file("tokenizer"))

lazy val anorm = project
  .in(file("."))
  .enablePlugins(Playdoc, Omnidoc)
  .dependsOn(tokenizer)

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(PlayDocsPlugin)
  .dependsOn(anorm)

name := "anorm"

libraryDependencies ++= Seq(
  "com.jsuereth" %% "scala-arm" % "1.4",
  "joda-time" % "joda-time" % "2.6", // TODO: scope as 'provided' ?
  "org.joda" % "joda-convert" % "1.7",

  "com.h2database" % "h2" % "1.4.182" % Test,
  "org.eu.acolyte" %% "jdbc-scala" % "1.0.32" % Test,
  "com.chuusai" % "shapeless" % "2.0.0" % Test cross CrossVersion.binaryMapped {
    case "2.10" => "2.10.4"
    case x => x
  }
) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, scalaMajor)) if scalaMajor >= 11 =>
    Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1")
  case _ => Nil
})

libraryDependencies ++= Seq(
  "specs2-core",
  "specs2-junit",
  "specs2-mock"
).map("org.specs2" %% _ % "2.4.9" % Test)

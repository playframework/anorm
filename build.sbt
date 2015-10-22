import AnormGeneration.{ generateFunctionAdapter => GFA }

val PlayVersion = playVersion(sys.props.getOrElse("play.version", "2.5.0-M1"))

lazy val acolyteVersion = "1.0.33-j7p"

lazy val `anorm-tokenizer` = project
  .in(file("tokenizer"))
  .enablePlugins(PlayLibrary)
  .settings(scalariformSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  )

lazy val anorm = project
  .in(file("core"))
  .enablePlugins(Playdoc, PlayLibrary)
  .settings(scalariformSettings: _*)
  .settings({
    sourceGenerators in Compile <+= (
      sourceManaged in Compile).map(m => Seq(GFA(m / "anorm")))
  })
  .settings(scalacOptions += "-Xlog-free-terms")
  .settings({
    libraryDependencies ++= Seq(
      "com.jsuereth" %% "scala-arm" % "1.4",
      "joda-time" % "joda-time" % "2.6",
      "org.joda" % "joda-convert" % "1.7",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1",
      "com.h2database" % "h2" % "1.4.182" % Test,
      "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test,
      "com.chuusai" %% "shapeless" % "2.0.0" % Test
    ) ++ Seq(
      "specs2-core",
      "specs2-junit"
    ).map("org.specs2" %% _ % "2.4.9" % Test)
  }).dependsOn(`anorm-tokenizer`)

lazy val `anorm-iteratee` = (project in file("iteratee"))
  .enablePlugins(PlayLibrary)
  .settings(scalariformSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-iteratees" % PlayVersion % "provided",
      "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test      
    ) ++ Seq(
      "specs2-core",
      "specs2-junit"
    ).map("org.specs2" %% _ % "2.4.9" % Test)
  ).dependsOn(anorm)

lazy val `anorm-parent` = (project in file("."))
  .enablePlugins(PlayRootProject)
  .aggregate(`anorm-tokenizer`, anorm, `anorm-iteratee`)
  .settings(scalaVersion := "2.11.7")

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(PlayDocsPlugin)
  .dependsOn(anorm)

playBuildRepoName in ThisBuild := "anorm"

import AnormGeneration.{ generateFunctionAdapter => GFA }
import Common._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaKeys.{
  binaryIssueFilters, previousArtifacts
}

lazy val acolyteVersion = "1.0.43-j7p"

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
  .settings(
  scalacOptions += "-Xlog-free-terms",
    binaryIssueFilters ++= Seq(
      // was private:
      ProblemFilters.exclude[FinalClassProblem]("anorm.Sql$MissingParameter"),
      missMeth("anorm.DefaultParameterValue.stringValue"/* deprecated */),
      missMeth("anorm.ParameterValue.stringValue"/* deprecated */),
      missMeth("anorm.BatchSql.apply"/* was deprecated */),
      missMeth("anorm.SqlQuery.getFilledStatement"/* deprecated 2.3.6 */),
      missMeth("anorm.SqlQuery.fetchSize"/* new field */),
      missMeth("anorm.SqlQuery.withFetchSize"/* new function */),
      missMeth("anorm.SqlQuery.prepare"/* private */),
      missMeth("anorm.SqlQuery.copy"/* private */),
      missMeth("anorm.SqlQuery.copy$default$4"/* private */),
      missMeth("anorm.Sql.getFilledStatement"/* deprecated 2.3.6 */),
      missMeth("anorm.Sql.executeInsert1"/* new function */),
      missMeth("anorm.Sql.preparedStatement"/* new function */),
      missMeth("anorm.Sql.asTry"/* private */),
      missMeth("anorm.WithResult.asTry$default$2"/* private */),
      missMeth("anorm.Sql.withResult"/* private */),
      missMeth("anorm.Sql.executeInsert1$default$3"/* new default */),
      missMeth("anorm.Sql.executeInsert2"/* new function */),
      missMeth("anorm.Sql.executeInsert2$default$3"/* new default */),
      missMeth("anorm.Cursor.onFirstRow"/* private */),
      missMeth("anorm.Cursor.apply"/* private */),
      ProblemFilters.exclude[MissingTypesProblem](
        "anorm.MetaData$"/* private */),
      missMeth("anorm.BatchSql.withFetchSize"/* new function */),
      missMeth("anorm.SqlQueryResult.apply"/* deprecated 2.4 */),
      missMeth("anorm.SimpleSql.getFilledStatement"/* deprecated 2.3.6 */),
      missMeth("anorm.SimpleSql.list"/* deprecated 2.3.5 */),
      missMeth("anorm.SimpleSql.single"/* deprecated 2.3.5 */),
      missMeth("anorm.SimpleSql.singleOpt"/* deprecated 2.3.5 */),
      missMeth("anorm.SimpleSql.apply"/* deprecated 2.4 */),
      missMeth("anorm.WithResult.withResult"/* new function */),
      missMeth("anorm.WithResult.foldWhile"/* new function */),
      missMeth("anorm.WithResult.fold"/* new function */),
      missMeth("anorm.WithResult.apply"/* deprecated 2.4 */),
      missMeth("anorm.WithResult.asTry"/*new function */),
      // New functions
      missMeth("anorm.Sql.unsafeStatement$default$2"),
      missMeth("anorm.Sql.unsafeResultSet"),
      missMeth("anorm.Sql.unsafeStatement")))
  .settings({
    libraryDependencies ++= Seq(
      "com.jsuereth" %% "scala-arm" % "2.0",
      "joda-time" % "joda-time" % "2.9.2",
      "org.joda" % "joda-convert" % "1.8.1",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
      "com.h2database" % "h2" % "1.4.182" % Test,
      "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test,
      "com.chuusai" %% "shapeless" % "2.3.2" % Test
    ) ++ Seq(
      "specs2-core",
      "specs2-junit"
    ).map("org.specs2" %% _ % "3.8.6" % Test)
  }).dependsOn(`anorm-tokenizer`)

lazy val `anorm-iteratee` = (project in file("iteratee"))
  .enablePlugins(PlayLibrary)
  .settings(scalariformSettings: _*)
  .settings(
    previousArtifacts := Set.empty,
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-iteratees" % "2.6.1",
      "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test      
    ) ++ Seq(
      "specs2-core",
      "specs2-junit"
    ).map("org.specs2" %% _ % "3.8.6" % Test)
  ).dependsOn(anorm)

lazy val `anorm-akka` = (project in file("akka"))
  .enablePlugins(PlayLibrary)
  .settings(scalariformSettings: _*)
  .settings(
    previousArtifacts := Set.empty,
    resolvers ++= Seq(
      // For Akka Stream Contrib TestKit (see akka/akka-stream-contrib/pull/51)
      "Tatami Releases".at(
        "https://raw.github.com/cchantep/tatami/master/snapshots")),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % "2.4.16" % "provided",
      "com.typesafe.akka" %% "akka-stream" % "2.4.16" % "provided",
      "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test
    ) ++ Seq(
      "specs2-core",
      "specs2-junit"
    ).map("org.specs2" %% _ % "3.8.6" % Test) ++ Seq(
      "com.typesafe.akka" %% "akka-stream-contrib" % "0.6" % Test)
  ).dependsOn(anorm)

lazy val `anorm-parent` = (project in file("."))
  .enablePlugins(PlayRootProject)
  .aggregate(`anorm-tokenizer`, anorm, `anorm-iteratee`, `anorm-akka`)
  .settings(
    scalaVersion in ThisBuild := "2.11.8",
    crossScalaVersions in ThisBuild := Seq("2.11.8", "2.12.1"),
    previousArtifacts := Set.empty)

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(PlayDocsPlugin)
  .dependsOn(anorm)

playBuildRepoName in ThisBuild := "anorm"

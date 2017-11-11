import AnormGeneration.{ generateFunctionAdapter => GFA }
import Common._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaKeys.{
  mimaBinaryIssueFilters, mimaPreviousArtifacts
}

val specs2Test = Seq(
  "specs2-core",
  "specs2-junit"
).map("org.specs2" %% _ % "3.9.4" % Test)

lazy val acolyteVersion = "1.0.46"
lazy val acolyte = "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test

lazy val `anorm-tokenizer` = project
  .in(file("tokenizer"))
  .enablePlugins(PlayLibrary, CopyPasteDetector)
  .settings(scalariformSettings(autoformat = true) ++ Seq(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  ))

lazy val anorm = project
  .in(file("core"))
  .enablePlugins(Playdoc, PlayLibrary, CopyPasteDetector)
  .settings(scalariformSettings(autoformat = true) ++ Seq(
    sourceGenerators in Compile += Def.task {
      Seq(GFA((sourceManaged in Compile).value / "anorm"))
    }.taskValue,
    scalacOptions += "-Xlog-free-terms",
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[MissingClassProblem]("anorm.MayErr"),
      ProblemFilters.exclude[MissingClassProblem]("anorm.MayErr$"),
      // was sealed:
      ProblemFilters.exclude[FinalClassProblem]("anorm.TupleFlattener"),
      ProblemFilters.exclude[FinalClassProblem]("anorm.NamedParameter"),
      // was deprecated:
      missMeth("anorm.Row.get"),
      missMeth("anorm.Row.getIndexed"),
      incoRet("anorm.Cursor#ResultRow.get"),
      incoRet("anorm.Cursor#ResultRow.getIndexed"),
      // was private:
      incoMeth("anorm.package.tokenize"),
      ProblemFilters.exclude[FinalClassProblem]("anorm.Sql$MissingParameter"),
      missMeth("anorm.DefaultParameterValue.stringValue"/* deprecated */),
      missMeth("anorm.ParameterValue.stringValue"/* deprecated */),
      missMeth("anorm.BatchSql.apply"/* was deprecated */),
      ProblemFilters.exclude[ReversedMissingMethodProblem]("anorm.BatchSql.bind"),
      ProblemFilters.exclude[DirectMissingMethodProblem]( // was deprecated
        "anorm.BatchSql.apply$default$2"),
      missMeth("anorm.SqlQuery.getFilledStatement"/* deprecated 2.3.6 */),
      ProblemFilters.exclude[DirectMissingMethodProblem]( // deprecated 2.3.6
        "anorm.SqlQuery.getFilledStatement$default$2"),
      missMeth("anorm.SqlQuery.fetchSize"/* new field */),
      missMeth("anorm.SqlQuery.withFetchSize"/* new function */),
      missMeth("anorm.SqlQuery.prepare"/* private */),
      missMeth("anorm.SqlQuery.copy"/* private */),
      missMeth("anorm.SqlQuery.copy$default$4"/* private */),
      incoRet( // private
        "anorm.SqlQuery.prepare$default$2"),
      missMeth("anorm.Sql.getFilledStatement"/* deprecated 2.3.6 */),
      ProblemFilters.exclude[DirectMissingMethodProblem]( // deprecated 2.3.6
        "anorm.Sql.getFilledStatement$default$2"),
      missMeth("anorm.Sql.executeInsert1"/* new function */),
      missMeth("anorm.Sql.preparedStatement"/* new function */),
      // private:
      incoMeth("anorm.Sql.asTry"),
      missMeth("anorm.WithResult.asTry$default$2"),
      missMeth("anorm.Sql.withResult"),
      // new default:
      missMeth("anorm.Sql.executeInsert1$default$3"),
      missMeth("anorm.Sql.executeInsert2$default$3"),
      missMeth("anorm.Sql.executeInsert2"/* new function */),
      // private:
      missMeth("anorm.Cursor.onFirstRow"),
      missMeth("anorm.Cursor.apply"),
      ProblemFilters.exclude[MissingTypesProblem]("anorm.MetaData$"),
      incoRet("anorm.MetaData.ms"),
      incoMeth("anorm.MetaData.this"),
      incoMeth("anorm.MetaData.apply"),
      incoMeth("anorm.MetaData.copy"),
      incoRet("anorm.MetaData.availableColumns"),
      incoRet("anorm.MetaData.copy$default$1"),
      missMeth("anorm.BatchSql.withFetchSize"/* new function */),
      missMeth("anorm.SqlQueryResult.apply"/* deprecated 2.4 */),
      missMeth("anorm.SimpleSql.getFilledStatement"/* deprecated 2.3.6 */),
      ProblemFilters.exclude[DirectMissingMethodProblem]( // deprecated 2.3
        "anorm.SimpleSql.getFilledStatement$default$2"),
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
      missMeth("anorm.Sql.unsafeStatement"),
      //missMeth("anorm.ToStatement.contramap"),
      missMeth("anorm.Column.mapResult"),
      missMeth("anorm.Column.map")
    ),
    libraryDependencies ++= Seq(
      "com.jsuereth" %% "scala-arm" % "2.0",
      "joda-time" % "joda-time" % "2.9.7",
      "org.joda" % "joda-convert" % "1.8.1",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
      "com.h2database" % "h2" % "1.4.193" % Test,
      acolyte,
      "com.chuusai" %% "shapeless" % "2.3.2" % Test
    ) ++ specs2Test
  )).dependsOn(`anorm-tokenizer`)

lazy val `anorm-iteratee` = (project in file("iteratee"))
  .enablePlugins(PlayLibrary, CopyPasteDetector)
  .settings(scalariformSettings(autoformat = true) ++ Seq(
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-iteratees" % "2.6.1",
      "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test
    ) ++ specs2Test
  )).dependsOn(anorm)

val akkaVer = "2.4.17"
lazy val `anorm-akka` = (project in file("akka"))
  .enablePlugins(PlayLibrary, CopyPasteDetector)
  .settings(scalariformSettings(autoformat = true) ++ Seq(
    mimaPreviousArtifacts := Set.empty,
    resolvers ++= Seq(
      // For Akka Stream Contrib TestKit (see akka/akka-stream-contrib/pull/51)
      "Tatami Releases".at(
        "https://raw.github.com/cchantep/tatami/master/snapshots")),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % akkaVer % "provided",
      "com.typesafe.akka" %% "akka-stream" % akkaVer % "provided",
      "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test
    ) ++ specs2Test ++ Seq(
      "com.typesafe.akka" %% "akka-stream-contrib" % "0.6" % Test)
  )).dependsOn(anorm)

lazy val pgVer = sys.env.get("POSTGRES_VERSION").getOrElse("9.4.1212")

lazy val `anorm-postgres` = (project in file("postgres"))
  .enablePlugins(PlayLibrary, CopyPasteDetector)
  .settings(scalariformSettings(autoformat = true) ++ Seq(
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % pgVer,
      "com.typesafe.play" %% "play-json" % "2.6.1"
    ) ++ specs2Test :+ acolyte
  )).dependsOn(anorm)

lazy val `anorm-parent` = (project in file("."))
  .enablePlugins(PlayRootProject)
  .aggregate(`anorm-tokenizer`, anorm, `anorm-iteratee`, `anorm-akka`)
  .settings(
  scalaVersion in ThisBuild := "2.12.4",
    crossScalaVersions in ThisBuild := Seq("2.11.11", "2.12.4"),
    mimaPreviousArtifacts := Set.empty)

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(PlayDocsPlugin)
  .settings(
  scalaVersion := "2.12.4"
).dependsOn(anorm)

Scapegoat.settings

playBuildRepoName in ThisBuild := "anorm"

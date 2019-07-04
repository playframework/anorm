import AnormGeneration.{ generateFunctionAdapter => GFA }
import Common._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaKeys.{
  mimaBinaryIssueFilters, mimaPreviousArtifacts
}

scalaVersion in ThisBuild := "2.12.7"

crossScalaVersions in ThisBuild := Seq(
  "2.11.12", (scalaVersion in ThisBuild).value)

val specs2Test = Seq(
  "specs2-core",
  "specs2-junit"
).map("org.specs2" %% _ % "4.3.6" % Test)

lazy val acolyteVersion = "1.0.51"
lazy val acolyte = "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test

lazy val `anorm-tokenizer` = project
  .in(file("tokenizer"))
  .enablePlugins(PlayLibrary)
  .settings(Seq(
    scalariformAutoformat := true,
    mimaPreviousArtifacts := {
      if (scalaVersion.value startsWith "2.13") {
        Set.empty
      } else {
        mimaPreviousArtifacts.value
      }
    },
    mimaBinaryIssueFilters ++= Seq( // private[anorm]
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("anorm.TokenizedStatement.apply"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("anorm.TokenizedStatement.tokenize"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("anorm.TokenizedStatement.tokens"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("anorm.TokenizedStatement.names"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("anorm.TokenizedStatement.copy$default$2"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("anorm.TokenizedStatement.copy"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("anorm.TokenizedStatement.copy$default$1"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("anorm.TokenizedStatement.this")
    ),
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  ))

lazy val anorm = project
  .in(file("core"))
  .enablePlugins(Playdoc, PlayLibrary)
  .settings(Seq(
    scalariformAutoformat := true,
    sourceGenerators in Compile += Def.task {
      Seq(GFA((sourceManaged in Compile).value / "anorm"))
    }.taskValue,
    scalacOptions += "-Xlog-free-terms",
    mimaPreviousArtifacts := {
      if (scalaVersion.value startsWith "2.13") {
        Set.empty
      } else {
        mimaPreviousArtifacts.value
      }
    },
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "anorm.ToStatementPriority0.urlToStatement"),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "anorm.ToStatementPriority0.uriToStatement"),
      ProblemFilters.exclude[FinalMethodProblem](
        "anorm.SimpleSql.preparedStatement"),
      ProblemFilters.exclude[FinalMethodProblem](
        "anorm.SimpleSql.preparedStatement$default$2"),
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
      missMeth("anorm.SqlQuery.prepare"/* private */),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]( // private
        "anorm.SqlQuery.prepare"),
      missMeth("anorm.SqlQuery.copy"/* private */),
      missMeth("anorm.SqlQuery.copy$default$4"/* private */),
      incoRet("anorm.SqlQuery.prepare$default$2"/* private */),
      ProblemFilters.exclude[DirectMissingMethodProblem]( // deprecated 2.3.8
        "anorm.SqlQuery.statement"),
      // private:
      incoMeth("anorm.Sql.asTry"),
      missMeth("anorm.WithResult.asTry$default$2"),
      missMeth("anorm.Sql.withResult"),
      missMeth("anorm.Cursor.onFirstRow"),
      missMeth("anorm.Cursor.apply"),
      ProblemFilters.exclude[MissingTypesProblem]("anorm.MetaData$"),
      incoRet("anorm.MetaData.ms"),
      incoMeth("anorm.MetaData.this"),
      incoMeth("anorm.MetaData.apply"),
      incoMeth("anorm.MetaData.copy"),
      incoRet("anorm.MetaData.availableColumns"),
      incoRet("anorm.MetaData.copy$default$1"),
      //missMeth("anorm.ToStatement.contramap"),
      missMeth("anorm.Column.mapResult"),
      missMeth("anorm.Column.map"),
      ProblemFilters.exclude[MissingClassProblem]( // macro
        "anorm.Macro$ImplicitResolver$2$ImplicitTransformer$"),
      ProblemFilters.exclude[MissingClassProblem]( // macro
        "anorm.Macro$ImplicitResolver$2$Implicit$"),
      ProblemFilters.exclude[MissingClassProblem]( // macro
        "anorm.Macro$ImplicitResolver$2$Implicit"),
      ProblemFilters.exclude[DirectMissingMethodProblem]( // private
        "anorm.Sql.asTry"),
      ProblemFilters.exclude[ReversedMissingMethodProblem]("anorm.JavaTimeToStatement.localDateToStatement")
    ),
    libraryDependencies ++= Seq(
      "com.jsuereth" %% "scala-arm" % "2.0",
      "joda-time" % "joda-time" % "2.9.7",
      "org.joda" % "joda-convert" % "1.8.1",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1",
      "com.h2database" % "h2" % "1.4.193" % Test,
      acolyte,
      "com.chuusai" %% "shapeless" % "2.3.3" % Test
    ) ++ specs2Test
  )).dependsOn(`anorm-tokenizer`)

lazy val `anorm-iteratee` = (project in file("iteratee"))
  .enablePlugins(PlayLibrary)
  .settings(Seq(
    scalariformAutoformat := true,
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-iteratees" % "2.6.1",
      "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test
    ) ++ specs2Test
  )).dependsOn(anorm)

val akkaVer = "2.4.12"
lazy val `anorm-akka` = (project in file("akka"))
  .enablePlugins(PlayLibrary)
  .settings(Seq(
    scalariformAutoformat := true,
    mimaPreviousArtifacts := Set.empty,
    resolvers ++= Seq(
      // For Akka Stream Contrib TestKit (see akka/akka-stream-contrib/pull/51)
      "Tatami Releases".at(
        "https://raw.github.com/cchantep/tatami/master/snapshots")),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % akkaVer % Provided,
      "com.typesafe.akka" %% "akka-stream" % akkaVer % Provided,
      "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test
    ) ++ specs2Test ++ Seq(
      "com.typesafe.akka" %% "akka-stream-contrib" % "0.6" % Test)
  )).dependsOn(anorm)

lazy val pgVer = sys.env.get("POSTGRES_VERSION").getOrElse("42.2.2")

lazy val `anorm-postgres` = (project in file("postgres"))
  .enablePlugins(PlayLibrary)
  .settings(Seq(
    scalariformAutoformat := true,
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % pgVer,
      "com.typesafe.play" %% "play-json" % "2.6.7"
    ) ++ specs2Test :+ acolyte
  )).dependsOn(anorm)

lazy val `anorm-parent` = (project in file("."))
  .enablePlugins(PlayRootProject, ScalaUnidocPlugin)
  .aggregate(`anorm-tokenizer`, anorm, `anorm-iteratee`, `anorm-akka`, `anorm-postgres`)
  .settings(
  scalaVersion in ThisBuild := "2.12.7",
    crossScalaVersions in ThisBuild := Seq("2.11.12", "2.12.7"),
    mimaPreviousArtifacts := Set.empty)

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(PlayDocsPlugin)
  .settings(
  scalaVersion := "2.12.7"
).dependsOn(anorm)

Scapegoat.settings

playBuildRepoName in ThisBuild := "anorm"

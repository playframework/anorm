import AnormGeneration.{ generateFunctionAdapter => GFA }
import Common._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaKeys.{
  mimaBinaryIssueFilters, mimaPreviousArtifacts
}

val specs2Test = Seq(
  "specs2-core",
  "specs2-junit"
).map("org.specs2" %% _ % "4.6.0" % Test)

lazy val acolyteVersion = "1.0.52"
lazy val acolyte = "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test

resolvers in ThisBuild ++= Seq(
  "Tatami Snapshots" at "https://raw.github.com/cchantep/tatami/master/snapshots")

lazy val `anorm-tokenizer` = project.in(file("tokenizer"))
  .settings(
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
  )

lazy val `anorm-core` = project.in(file("core"))
  .settings(
    name := "anorm",
    scalariformAutoformat := true,
    sourceGenerators in Compile += Def.task {
      Seq(GFA((sourceManaged in Compile).value / "anorm"))
    }.taskValue,
    scalacOptions += "-Xlog-free-terms",
    scalacOptions += { // Silencer
      "-P:silencer:globalFilters=missing\\ in\\ object\\ ToSql\\ is\\ deprecated;possibilities\\ in\\ class\\ ColumnNotFound\\ is\\ deprecated;DeprecatedSqlParser\\ in\\ package\\ anorm\\ is\\ deprecated;constructor\\ deprecatedName\\ in\\ class\\ deprecatedName\\ is\\ deprecated"
    },
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
      "com.jsuereth" %% "scala-arm" % "2.1-SNAPSHOT",
      "joda-time" % "joda-time" % "2.10.3",
      "org.joda" % "joda-convert" % "2.2.1",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",
      "com.h2database" % "h2" % "1.4.199" % Test,
      acolyte,
      "com.chuusai" %% "shapeless" % "2.3.3" % Test
    ) ++ specs2Test
  ).dependsOn(`anorm-tokenizer`)

lazy val `anorm-iteratee` = (project in file("iteratee"))
  .settings(
    sourceDirectory := {
      if (scalaVersion.value startsWith "2.13.") new java.io.File("/no/sources")
      else sourceDirectory.value
    },
    scalariformAutoformat := true,
    mimaPreviousArtifacts := {
      if (scalaVersion.value startsWith "2.13.") Set.empty[ModuleID]
      else Set(organization.value %% name.value % "2.6.0")
    },
    publish := (Def.taskDyn {
      val p = publish.value
      val ver = scalaVersion.value

      Def.task[Unit] {
        if (ver startsWith "2.13.") ({})
        else p
      }
    }).value,
    publishTo := (Def.taskDyn {
      val p = publishTo.value
      val ver = scalaVersion.value

      Def.task {
        if (ver startsWith "2.13.") None
        else p
      }
    }).value,
    libraryDependencies ++= {
      if (scalaVersion.value startsWith "2.13.") Seq.empty[ModuleID]
      else Seq(
        "com.typesafe.play" %% "play-iteratees" % "2.6.1",
        acolyte
      ) ++ specs2Test
    }
  ).dependsOn(`anorm-core`)

// ---

lazy val akkaVer = Def.setting[String] {
  sys.env.get("AKKA_VERSION").getOrElse {
    if (scalaVersion.value startsWith "2.11.") "2.4.10"
    else "2.5.23"
  }
}

val akkaContribVer = Def.setting[String] {
  if (akkaVer.value startsWith "2.5") "0.10+2-78d1b592"
  else "0.6-6-g12a86f9-SNAPSHOT"
}

lazy val `anorm-akka` = (project in file("akka"))
  .settings(
    scalariformAutoformat := true,
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= Seq("akka-testkit", "akka-stream").map { m =>
      "com.typesafe.akka" %% m % akkaVer.value % Provided
    },
    libraryDependencies ++= (acolyte +: specs2Test) ++ Seq(
      "com.typesafe.akka" %% "akka-stream-contrib" % akkaContribVer.value % Test)
  ).dependsOn(`anorm-core`)

// ---

lazy val pgVer = sys.env.get("POSTGRES_VERSION").getOrElse("42.2.6")

val playVer = Def.setting[String] {
  if (scalaVersion.value startsWith "2.13") "2.7.3"
  else "2.6.7"
}

lazy val `anorm-postgres` = (project in file("postgres"))
  .settings(
    scalariformAutoformat := true,
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= {
      val playJsonVer = {
        if (scalaVersion.value startsWith "2.13") "2.7.4"
        else "2.6.7"
      }

      Seq(
        "org.postgresql" % "postgresql" % pgVer,
        "com.typesafe.play" %% "play-json" % playJsonVer
      ) ++ specs2Test :+ acolyte
    }
  ).dependsOn(`anorm-core`)

lazy val `anorm-parent` = (project in file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(
    `anorm-tokenizer`, `anorm-core`,
    `anorm-iteratee`, `anorm-akka`,
    `anorm-postgres`)
  .settings(
    mimaPreviousArtifacts := Set.empty)

lazy val docs = project.in(file("docs"))
  .enablePlugins(Playdoc)
  .configs(Docs)
  .settings(
    name := "anorm-docs",
    unmanagedSourceDirectories in Test ++= {
      val manualDir = baseDirectory.value / "manual" / "working"

      (manualDir / "javaGuide" ** "code").get ++ (
        manualDir / "scalaGuide" ** "code").get
    },
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-jdbc" % playVer.value % Test,
      "com.typesafe.play" %% "play-specs2" % playVer.value % Test,
      "com.h2database" % "h2" % "1.4.199"
    )
  )
  .dependsOn(`anorm-core`)

Scapegoat.settings

playBuildRepoName in ThisBuild := "anorm"

import AnormGeneration.{ generateFunctionAdapter => GFA }
import Common._

import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaKeys.{
  mimaBinaryIssueFilters, mimaPreviousArtifacts
}

/* TODO
// Scalafix
inThisBuild(
  List(
    //scalaVersion := "2.13.3",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixDependencies ++= Seq(
      "com.github.liancheng" %% "organize-imports" % "0.5.0")
  )
)
 */

val specs2Test = Seq(
  "specs2-core",
  "specs2-junit"
).map("org.specs2" %% _ % "4.10.6" % Test)

lazy val acolyteVersion = "1.0.57"
lazy val acolyte = "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test

ThisBuild / resolvers ++= Seq(
  "Tatami Snapshots" at "https://raw.github.com/cchantep/tatami/master/snapshots")

ThisBuild / mimaFailOnNoPrevious := false

lazy val `anorm-tokenizer` = project.in(file("tokenizer"))
  .settings(
    scalariformAutoformat := true,
    mimaPreviousArtifacts := {
      if (scalaBinaryVersion.value == "2.13") {
        Set.empty
      } else {
        mimaPreviousArtifacts.value
      }
    },
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  )

// ---

val armShading = Seq(
  libraryDependencies += "com.jsuereth" %% "scala-arm" % "2.1-SNAPSHOT",
  assembly / test := {},
  assembly / assemblyOption ~= {
    _.withIncludeScala(false) // java libraries shouldn't include scala
  },
  (assembly / assemblyJarName) := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((maj, min)) => s"anorm_${maj}.${min}-${version.value}.jar"
      case _                => "anorm.jar"
    }
  },
  assembly / assemblyShadeRules := Seq.empty,
  (assembly / assemblyExcludedJars) := (assembly / fullClasspath).value.filter {
    !_.data.getName.startsWith("scala-arm")
  },
  (assembly / assemblyMergeStrategy) := {
    val tokPrefixes = Seq(
      "PercentToken", "Show", "StatementToken", "StringShow",
      "StringToken", "TokenGroup", "TokenizedStatement")

    {
      case path if tokPrefixes.exists(p => path.startsWith(s"anorm/$p")) =>
        MergeStrategy.discard

      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  },
  pomPostProcess := {
    val excludeGroups = Seq(
      "com.github.ghik",
      "com.jsuereth",
      "com.sksamuel.scapegoat")

    XmlUtil.transformPomDependencies { d =>
      (d \ "groupId").headOption.collect {
        case g if !excludeGroups.contains(g.text) => d
      }
    }
  },
  makePom := makePom.dependsOn(assembly).value,
  (Compile / packageBin) := assembly.value
)

lazy val parserCombinatorsVer = Def.setting[String] {
  if (scalaBinaryVersion.value startsWith "2") {
    "1.1.2"
  } else {
    "2.1.1"
  }
}

lazy val `anorm-core` = project.in(file("core"))
  .settings(Seq(
    name := "anorm",
    scalariformAutoformat := true,
    (Compile / sourceGenerators) += Def.task {
      Seq(GFA((Compile / sourceManaged).value / "anorm"))
    }.taskValue,
    scalacOptions ++= Seq(
      "-Xlog-free-terms",
      "-P:silencer:globalFilters=missing\\ in\\ object\\ ToSql\\ is\\ deprecated;possibilities\\ in\\ class\\ ColumnNotFound\\ is\\ deprecated;DeprecatedSqlParser\\ in\\ package\\ anorm\\ is\\ deprecated;constructor\\ deprecatedName\\ in\\ class\\ deprecatedName\\ is\\ deprecated"
    ),
    (Test / scalacOptions) ++= {
      if (scalaBinaryVersion.value == "2.13") {
        Seq(
          "-Ypatmat-exhaust-depth", "off",
          "-P:silencer:globalFilters=multiarg\\ infix\\ syntax")
      } else {
        Seq.empty
      }
    },
    mimaPreviousArtifacts := {
      if (scalaBinaryVersion.value == "2.13") {
        Set.empty
      } else {
        mimaPreviousArtifacts.value
      }
    },
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[IncompatibleSignatureProblem](
        "anorm.SqlStatementParser.elem"),
      ProblemFilters.exclude[IncompatibleSignatureProblem](
        "anorm.SqlStatementParser.accept"),
      // ---
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
      "joda-time" % "joda-time" % "2.10.14",
      "org.joda" % "joda-convert" % "2.2.2",
      "org.scala-lang.modules" %% "scala-parser-combinators" % parserCombinatorsVer.value,
      "com.h2database" % "h2" % "2.1.212" % Test,
      acolyte,
      "com.chuusai" %% "shapeless" % "2.3.9" % Test
    ) ++ specs2Test
  ) ++ armShading).dependsOn(`anorm-tokenizer`)

lazy val `anorm-iteratee` = (project in file("iteratee"))
  .settings(
    sourceDirectory := {
      if (scalaBinaryVersion.value == "2.13") new java.io.File("/no/sources")
      else sourceDirectory.value
    },
    scalariformAutoformat := true,
    mimaPreviousArtifacts := {
      if (scalaBinaryVersion.value == "2.13") Set.empty[ModuleID]
      else Set(organization.value %% name.value % "2.6.0")
    },
    publish := (Def.taskDyn {
      val p = publish.value
      val ver = scalaBinaryVersion.value

      Def.task[Unit] {
        if (ver == "2.13") ({})
        else p
      }
    }).value,
    publishTo := (Def.taskDyn {
      val p = publishTo.value

      Def.task {
        if (scalaBinaryVersion.value == "2.13") None
        else p
      }
    }).value,
    libraryDependencies ++= {
      if (scalaBinaryVersion.value == "2.13") Seq.empty[ModuleID]
      else Seq(
        "com.typesafe.play" %% "play-iteratees" % "2.6.1",
        acolyte
      ) ++ specs2Test
    }
  ).dependsOn(`anorm-core`)

// ---

lazy val akkaVer = Def.setting[String] {
  sys.env.get("AKKA_VERSION").getOrElse {
    if (scalaBinaryVersion.value == "2.11") "2.4.20"
    else "2.5.32"
  }
}

val akkaContribVer = Def.setting[String] {
  if (akkaVer.value startsWith "2.5") "0.11+4-91b2f9fa"
  else "0.10"
}

lazy val `anorm-akka` = (project in file("akka"))
  .settings(
    scalariformAutoformat := true,
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= Seq("akka-testkit", "akka-stream").map { m =>
      "com.typesafe.akka" %% m % akkaVer.value % Provided
    },
    libraryDependencies ++= (acolyte +: specs2Test) ++ Seq(
      "com.typesafe.akka" %% "akka-stream-contrib" % akkaContribVer.value % Test),
    scalacOptions += "-P:silencer:globalFilters=deprecated"
  ).dependsOn(`anorm-core`)

// ---

lazy val pgVer = sys.env.get("POSTGRES_VERSION").getOrElse("42.3.3")

val playVer = Def.setting[String] {
  if (scalaVersion.value startsWith "2.13") "2.7.3"
  else "2.6.14"
}

lazy val `anorm-postgres` = (project in file("postgres"))
  .settings(
    scalariformAutoformat := true,
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= {
      val playJsonVer = {
        if (scalaBinaryVersion.value == "2.13") "2.9.2"
        else "2.6.7"
      }

      Seq(
        "org.postgresql" % "postgresql" % pgVer,
        "com.typesafe.play" %% "play-json" % playJsonVer
      ) ++ specs2Test :+ acolyte
    }
).dependsOn(`anorm-core`)

lazy val `anorm-enumeratum` = (project in file("enumeratum"))
  .settings(
    scalariformAutoformat := true,
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= Seq(
      "com.beachape" %% "enumeratum" % "1.7.0",
      acolyte) ++ specs2Test
).dependsOn(`anorm-core`)

// ---

lazy val `anorm-parent` = (project in file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(
    `anorm-tokenizer`, `anorm-core`,
    `anorm-iteratee`, `anorm-akka`,
    `anorm-postgres`, `anorm-enumeratum`)
  .settings(
    mimaPreviousArtifacts := Set.empty)

lazy val docs = project.in(file("docs"))
  .enablePlugins(Playdoc)
  .configs(Docs)
  .settings(
    name := "anorm-docs",
    (Test / unmanagedSourceDirectories) ++= {
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

ThisBuild / playBuildRepoName := "anorm"

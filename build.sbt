// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

import AnormGeneration.{ generateFunctionAdapter => GFA }
import Common._

import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaKeys.{ mimaBinaryIssueFilters, mimaPreviousArtifacts }

// Scalafix
inThisBuild(
  List(
    // scalaVersion := "2.13.3",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

val specs2Test = Seq(
  "specs2-core",
  "specs2-junit",
  "specs2-matcher-extra"
).map("org.specs2" %% _ % "4.10.6" % Test cross (CrossVersion.for3Use2_13))
  .map(_.exclude("org.scala-lang.modules", "*"))

val common = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-encoding", "utf8") ++
    (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) => Seq("-Xsource:3", "-Xmigration")
      case _             => Seq.empty
    }),
  javacOptions ++= Seq("-encoding", "UTF-8", "-Xlint:-options"),
)

lazy val acolyteVersion = "1.2.9"
lazy val acolyte        = "org.eu.acolyte" %% "jdbc-scala" % acolyteVersion % Test

ThisBuild / resolvers ++= Seq("Tatami Snapshots".at("https://raw.github.com/cchantep/tatami/master/snapshots"))

ThisBuild / mimaFailOnNoPrevious := false

lazy val `anorm-tokenizer` = project
  .in(file("tokenizer"))
  .settings(common)
  .settings(
    mimaPreviousArtifacts := {
      if (scalaBinaryVersion.value == "3") {
        Set.empty
      } else {
        mimaPreviousArtifacts.value
      }
    },
    scalacOptions ++= {
      if (scalaBinaryVersion.value == "3") {
        Seq.empty
      } else {
        Seq(
          "-P:silencer:globalFilters=type Seq in package scala has changed semantics in version 2.13.0",
        )
      },
    },
    libraryDependencies += {
      if (scalaBinaryVersion.value == "3") {
        "org.scala-lang" %% "scala3-compiler" % scalaVersion.value % Provided
      } else {
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      }
    }
  )

// ---

val armShading = Seq(
  libraryDependencies += ("com.jsuereth" %% "scala-arm" % "2.1-SNAPSHOT").cross(CrossVersion.for3Use2_13),
  assembly / test                        := {},
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
    val tokPrefixes =
      Seq("PercentToken", "Show", "StatementToken", "StringShow", "StringToken", "TokenGroup", "TokenizedStatement")

    {
      case path if tokPrefixes.exists(p => path.startsWith(s"anorm/$p")) =>
        MergeStrategy.discard

      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  },
  pomPostProcess := {
    val excludeGroups = Seq("com.github.ghik", "com.jsuereth", "com.sksamuel.scapegoat")

    XmlUtil.transformPomDependencies { d =>
      (d \ "groupId").headOption.collect {
        case g if !excludeGroups.contains(g.text) => d
      }
    }
  },
  makePom                := makePom.dependsOn(assembly).value,
  (Compile / packageBin) := assembly.value
)

lazy val parserCombinatorsVer = Def.setting[String] {
  if (scalaBinaryVersion.value.startsWith("2")) {
    "1.1.2"
  } else {
    "2.3.0"
  }
}

lazy val coreMimaFilter: ProblemFilter = {
  case MissingClassProblem(old) =>
    !old.fullName.startsWith("resource.") &&
    old.fullName.indexOf("Macro") == -1 &&
    !old.fullName.startsWith("anorm.macros.")

  case _ => true
}

lazy val xmlVer = Def.setting[String] {
  if (scalaBinaryVersion.value == "2.11") {
    "1.3.1"
  } else {
    "2.2.0"
  }
}

lazy val `anorm-core` = project
  .in(file("core"))
  .settings(common)
  .settings(
    Seq(
      name := "anorm",
      (Compile / sourceGenerators) += Def.task {
        Seq(GFA((Compile / sourceManaged).value / "anorm"))
      }.taskValue,
      scaladocExtractorSkipToken := {
        if (scalaBinaryVersion.value == "3") {
          "// skip-doc-5f98a5e"
        } else {
          scaladocExtractorSkipToken.value
        }
      },
      scalacOptions ++= {
        if (scalaBinaryVersion.value == "3") {
          Seq.empty
          Seq(
            "-Wconf:cat=deprecation&msg=.*(reflectiveSelectableFromLangReflectiveCalls|DeprecatedSqlParser|missing .*ToSql).*:s"
          )
        } else {
          Seq(
            "-Xlog-free-terms",
            "-P:silencer:globalFilters=missing\\ in\\ object\\ ToSql\\ is\\ deprecated;possibilities\\ in\\ class\\ ColumnNotFound\\ is\\ deprecated;DeprecatedSqlParser\\ in\\ package\\ anorm\\ is\\ deprecated;constructor\\ deprecatedName\\ in\\ class\\ deprecatedName\\ is\\ deprecated;.*method ~> in class Parser has changed semantics.*;.*method <~ in class Parser has changed semantics.*;.*method ~ in class Parser has changed semantics.*;.*package object inheritance is deprecated.*;type Seq in package scala has changed semantics in version 2.13.0",
          )
        }
      },
      Test / scalacOptions ++= {
        if (scalaBinaryVersion.value == "2.13") {
          Seq("-Ypatmat-exhaust-depth", "off", "-P:silencer:globalFilters=multiarg\\ infix\\ syntax")
        } else {
          Seq.empty
        }
      },
      mimaPreviousArtifacts := {
        if (scalaBinaryVersion.value == "3") {
          Set.empty
        } else {
          mimaPreviousArtifacts.value
        }
      },
      mimaBinaryIssueFilters ++= Seq(
        ProblemFilters.exclude[MissingClassProblem](f"anorm.package$$ResultSetResource$$"),
        ProblemFilters.exclude[MissingClassProblem](f"anorm.package$$StatementResource$$"),
        ProblemFilters.exclude[MissingClassProblem](f"anorm.package$$StringWrapper2$$"),
        ProblemFilters.exclude[MissingClassProblem](f"anorm.package$$TimestampWrapper1$$"),
        ProblemFilters.exclude[MissingClassProblem](f"anorm.package$$TimestampWrapper2$$"),
        ProblemFilters.exclude[MissingClassProblem](f"anorm.package$$features$$"),
        // ---
        ProblemFilters.exclude[IncompatibleSignatureProblem]("anorm.SqlStatementParser.elem"),
        ProblemFilters.exclude[IncompatibleSignatureProblem]("anorm.SqlStatementParser.accept"),
        // ---
        ProblemFilters.exclude[ReversedMissingMethodProblem]("anorm.ToStatementPriority0.urlToStatement"),
        ProblemFilters.exclude[ReversedMissingMethodProblem]("anorm.ToStatementPriority0.uriToStatement"),
        ProblemFilters.exclude[FinalMethodProblem]("anorm.SimpleSql.preparedStatement"),
        ProblemFilters.exclude[FinalMethodProblem]("anorm.SimpleSql.preparedStatement$default$2"),
        ProblemFilters.exclude[MissingClassProblem]("anorm.MayErr"),
        ProblemFilters.exclude[MissingClassProblem]("anorm.MayErr$"),
        // was private:
        incoMeth("anorm.package.tokenize"),
        ProblemFilters.exclude[FinalClassProblem]("anorm.Sql$MissingParameter"),
        missMeth("anorm.DefaultParameterValue.stringValue" /* deprecated */ ),
        missMeth("anorm.ParameterValue.stringValue" /* deprecated */ ),
        missMeth("anorm.BatchSql.apply" /* was deprecated */ ),
        ProblemFilters.exclude[ReversedMissingMethodProblem]("anorm.BatchSql.bind"),
        missMeth("anorm.SqlQuery.prepare" /* private */ ),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]( // private
          "anorm.SqlQuery.prepare"
        ),
        missMeth("anorm.SqlQuery.copy" /* private */ ),
        missMeth("anorm.SqlQuery.copy$default$4" /* private */ ),
        incoRet("anorm.SqlQuery.prepare$default$2" /* private */ ),
        ProblemFilters.exclude[DirectMissingMethodProblem]( // deprecated 2.3.8
          "anorm.SqlQuery.statement"
        ),
        incoRet("anorm.ParameterValue.apply"),
        // private:
        ProblemFilters.exclude[DirectMissingMethodProblem]( // private
          "anorm.Sql.asTry"
        ),
        ProblemFilters.exclude[ReversedMissingMethodProblem]("anorm.JavaTimeToStatement.localDateToStatement"),
        coreMimaFilter,
        // was deprecated
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("anorm.ColumnNotFound.copy"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("anorm.ColumnNotFound.copy$default$2")
      ),
      libraryDependencies ++= Seq(
        "joda-time"               % "joda-time"                % "2.12.7",
        "org.joda"                % "joda-convert"             % "2.2.3",
        "org.scala-lang.modules" %% "scala-parser-combinators" % parserCombinatorsVer.value,
        "org.scala-lang.modules" %% "scala-xml"                % xmlVer.value % Test,
        "com.h2database"          % "h2"                       % "2.2.224"    % Test,
        acolyte
      ) ++ specs2Test,
    ) ++ armShading
  )
  .dependsOn(`anorm-tokenizer`)

lazy val `anorm-iteratee` = (project in file("iteratee"))
  .settings(common)
  .settings(
    sourceDirectory := {
      val v = scalaBinaryVersion.value

      if (v == "3" || v == "2.13") new java.io.File("/no/sources")
      else sourceDirectory.value
    },
    mimaPreviousArtifacts := {
      val v = scalaBinaryVersion.value

      if (v == "3" || v == "2.13") Set.empty[ModuleID]
      else Set(organization.value %% name.value % "2.6.10")
    },
    publish / skip := {
      val v = scalaBinaryVersion.value

      v == "3" || v == "2.13"
    },
    libraryDependencies ++= {
      val v = scalaBinaryVersion.value

      if (v == "3" || v == "2.13") Seq.empty[ModuleID]
      else
        Seq(
          "com.typesafe.play"      %% "play-iteratees" % "2.6.1",
          "org.scala-lang.modules" %% "scala-xml"      % xmlVer.value % Test,
          acolyte
        ) ++ specs2Test
    }
  )
  .dependsOn(`anorm-core`)

// ---

lazy val akkaVer = Def.setting[String] {
  sys.env.get("AKKA_VERSION").getOrElse {
    val v = scalaBinaryVersion.value

    if (v == "2.11") "2.4.20"
    else if (v == "3") "2.6.19"
    else "2.5.32"
  }
}

lazy val `anorm-akka` = (project in file("akka"))
  .settings(common)
  .settings(
    mimaPreviousArtifacts := {
      if (scalaBinaryVersion.value == "3") {
        Set.empty
      } else {
        mimaPreviousArtifacts.value
      }
    },
    libraryDependencies ++= Seq("akka-testkit", "akka-stream").map { m =>
      ("com.typesafe.akka" %% m % akkaVer.value % Provided).exclude("org.scala-lang.modules", "*")
    },
    libraryDependencies ++= Seq(
      acolyte,
      "org.scala-lang.modules" %% "scala-xml"           % xmlVer.value  % Test,
      ("com.typesafe.akka"     %% "akka-stream-testkit" % akkaVer.value % Test).exclude("org.scala-lang.modules", "*")
    ) ++ specs2Test,
    scalacOptions ++= {
      if (scalaBinaryVersion.value == "3") {
        Seq("-Wconf:cat=deprecation&msg=.*(onDownstreamFinish|ActorMaterializer).*:s")
      } else {
        Seq("-P:silencer:globalFilters=deprecated")
      }
    },
    Test / unmanagedSourceDirectories += {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n < 13 =>
          (Test / sourceDirectory).value / "scala-2.13-"

        case _ =>
          (Test / sourceDirectory).value / "scala-2.13+"

      }
    },
    Test / unmanagedSourceDirectories += {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 11)) =>
          (Test / sourceDirectory).value / "scala-2.12-"

        case _ =>
          (Test / sourceDirectory).value / "scala-2.12+"

      }
    }
  )
  .dependsOn(`anorm-core`)

lazy val pekkoVer = Def.setting[String]("1.0.1")

lazy val pekkoEnabled = Def.setting[Boolean] {
  val v = scalaBinaryVersion.value

  v != "2.11" && v != "2.12"
}

lazy val `anorm-pekko` = (project in file("pekko"))
  .settings(common)
  .settings(
    mimaPreviousArtifacts := Set.empty,
    sourceDirectory := {
      if (!pekkoEnabled.value) new java.io.File("/no/sources")
      else sourceDirectory.value
    },
    publishArtifact := pekkoEnabled.value,
    publish := Def.taskDyn {
      val ver = scalaBinaryVersion.value
      val go  = publish.value

      Def.task {
        if (pekkoEnabled.value) {
          go
        }
      }
    }.value,
    libraryDependencies ++= {
      if (pekkoEnabled.value) {
        Seq("pekko-testkit", "pekko-stream").map { m =>
          ("org.apache.pekko" %% m % pekkoVer.value % Provided).exclude("org.scala-lang.modules", "*")
        }
      } else {
        Seq.empty
      }
    },
    libraryDependencies ++= {
      if (pekkoEnabled.value) {
        Seq(
          acolyte,
          "org.scala-lang.modules" %% "scala-xml"            % xmlVer.value   % Test,
          "org.apache.pekko"       %% "pekko-stream-testkit" % pekkoVer.value % Test
        ) ++ specs2Test
      } else {
        Seq.empty
      }
    },
    scalacOptions ++= {
      if (scalaBinaryVersion.value == "3") {
        Seq("-Wconf:cat=deprecation&msg=.*(onDownstreamFinish|ActorMaterializer).*:s")
      } else {
        Seq("-P:silencer:globalFilters=deprecated")
      }
    },
    Test / unmanagedSourceDirectories ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n < 13 =>
          Seq((Test / sourceDirectory).value / "scala-2.13-")

        case _ =>
          Seq((Test / sourceDirectory).value / "scala-2.13+")

      }
    }
  )
  .dependsOn(`anorm-core`)

// ---

lazy val pgVer = sys.env.get("POSTGRES_VERSION").getOrElse("42.7.1")

val playVer = Def.setting[String] {
  if (scalaBinaryVersion.value == "2.13") "2.7.3"
  else "2.6.14"
}

lazy val `anorm-postgres` = (project in file("postgres"))
  .settings(common)
  .settings(
    mimaPreviousArtifacts := Set.empty,
    scalacOptions ++= {
      if (scalaBinaryVersion.value == "3") {
        Seq.empty
      } else {
        Seq(
          "-P:silencer:globalFilters=.*package object inheritance is deprecated.*",
        )
      }
    },
    libraryDependencies ++= {
      val v = scalaBinaryVersion.value

      val playJsonVer = {
        if (v == "2.13") "2.9.2"
        else if (v == "3") "2.10.1"
        else "2.6.7"
      }

      Seq(
        "org.postgresql"          % "postgresql" % pgVer,
        "com.typesafe.play"      %% "play-json"  % playJsonVer,
        "org.scala-lang.modules" %% "scala-xml"  % xmlVer.value % Test
      ) ++ specs2Test :+ acolyte
    }
  )
  .dependsOn(`anorm-core`)

lazy val `anorm-enumeratum` = (project in file("enumeratum"))
  .settings(common)
  .settings(
    sourceDirectory := {
      if (scalaBinaryVersion.value == "3") new java.io.File("/no/sources")
      else sourceDirectory.value
    },
    publish / skip        := { scalaBinaryVersion.value == "3" },
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= {
      if (scalaBinaryVersion.value != "3") {
        Seq(
          "org.scala-lang.modules" %% "scala-xml"  % xmlVer.value % Test,
          "com.beachape"           %% "enumeratum" % "1.7.2",
          acolyte
        ) ++ specs2Test
      } else {
        Seq.empty
      }
    }
  )
  .dependsOn(`anorm-core`)

// ---

lazy val `anorm-parent` = (project in file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(
    `anorm-tokenizer`,
    `anorm-core`,
    `anorm-iteratee`,
    `anorm-akka`,
    `anorm-pekko`,
    `anorm-postgres`,
    `anorm-enumeratum`
  )
  .settings(common)
  .settings(
    mimaPreviousArtifacts := Set.empty,
    (Compile / headerSources) ++=
      ((baseDirectory.value ** ("*.properties" || "*.md" || "*.sbt"))
        --- (baseDirectory.value ** "target" ** "*")
        --- (baseDirectory.value / ".github" ** "*")
        --- (baseDirectory.value / "docs" ** "*")).get ++
        (baseDirectory.value / "project" ** "*.scala" --- (baseDirectory.value ** "target" ** "*")).get
  )

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(Playdoc)
  .configs(Docs)
  .settings(common)
  .settings(
    name := "anorm-docs",
    (Test / unmanagedSourceDirectories) ++= {
      val manualDir = baseDirectory.value / "manual" / "working"

      (manualDir / "javaGuide" ** "code").get ++ (manualDir / "scalaGuide" ** "code").get
    },
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-jdbc"   % playVer.value % Test,
      "com.typesafe.play" %% "play-specs2" % playVer.value % Test,
      "com.h2database"     % "h2"          % "1.4.199"
    ),
    (Compile / headerSources) ++=
      ((baseDirectory.value ** ("*.md" || "*.scala" || "*.xml"))
        --- (baseDirectory.value ** "target" ** "*")).get
  )
  .dependsOn(`anorm-core`)

addCommandAlias(
  "validateCode",
  List(
    "scalafixAll -check",
    "scalafmtSbtCheck",
    "+scalafmtCheckAll",
    "+headerCheckAll",
    "docs/headerCheckAll",
    "docs/scalafmtCheckAll"
  ).mkString(";")
)

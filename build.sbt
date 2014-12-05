lazy val anorm = project
  .in(file("."))

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(PlayDocsPlugin)
  .dependsOn(anorm)

name := "anorm"

libraryDependencies ++= Seq(
  "com.jsuereth" %% "scala-arm" % "1.4",
  "joda-time" % "joda-time" % "2.6",
  "org.joda" % "joda-convert" % "1.7",

  "com.h2database" % "h2" % "1.4.182" % Test,
  "org.eu.acolyte" %% "jdbc-scala" % "1.0.30" % Test,
  "com.chuusai" % "shapeless" % "2.0.0" % Test cross CrossVersion.binaryMapped {
    case "2.10" => "2.10.4"
    case x => x
  }
)

libraryDependencies ++= Seq(
  "specs2-core",
  "specs2-junit",
  "specs2-mock"
).map("org.specs2" %% _ % "2.4.9" % Test)

scalariformSettings

// Release settings

releaseSettings
ReleaseKeys.crossBuild := true
ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value
ReleaseKeys.tagName := (version in ThisBuild).value

// Publish settings
publishTo := {
  if (isSnapshot.value) Some(Opts.resolver.sonatypeSnapshots)
  else Some(Opts.resolver.sonatypeStaging)
}
homepage := Some(url("https://github.com/playframework/anorm"))
licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
pomExtra := {
  <scm>
    <url>https://github.com/playframework/anorm</url>
    <connection>scm:git:git@github.com:playframework/anorm.git</connection>
  </scm>
  <developers>
    <developer>
      <id>playframework</id>
      <name>Play Framework Team</name>
      <url>https://github.com/playframework</url>
    </developer>
  </developers>
}
pomIncludeRepository := { _ => false }

// Aggregated documentation
projectID := {
  val baseUrl = "https://github.com/playframework/anorm"
  val sourceTree = if (isSnapshot.value) "master" else "v" + version.value
  val sourceDirectory = IO.relativize((baseDirectory in ThisBuild).value, baseDirectory.value).getOrElse("")
  val sourceUrl = s"$baseUrl/tree/$sourceTree/$sourceDirectory"
  projectID.value.extra("info.sourceUrl" -> sourceUrl)
}

val packagePlaydoc = TaskKey[File]("package-playdoc", "Package play documentation")

Defaults.packageTaskSettings(packagePlaydoc, mappings in packagePlaydoc)
mappings in packagePlaydoc := {
  val base = (baseDirectory in ThisBuild).value / "docs"
  (base / "manual").***.get pair relativeTo(base)
}
artifactClassifier in packagePlaydoc := Some("playdoc")
artifact in packagePlaydoc ~= { _.copy(configurations = Seq(Docs)) }

addArtifact(artifact in packagePlaydoc, packagePlaydoc)

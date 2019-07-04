name := "anorm-docs"

libraryDependencies ++= Seq(
  component("play-jdbc") % "test",
  component("play-specs2") % "test",
  "com.h2database" % "h2" % "1.4.199"
)

PlayDocsKeys.javaManualSourceDirectories  := (baseDirectory.value / "manual" / "working" / "javaGuide" ** "code").get
PlayDocsKeys.scalaManualSourceDirectories := (baseDirectory.value / "manual" / "working" / "scalaGuide" ** "code").get


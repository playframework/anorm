name := "anorm-java8"

libraryDependencies ++= Seq(
  "specs2-core",
  "specs2-junit"
).map("org.specs2" %% _ % "2.4.9" % Test)

libraryDependencies += "org.eu.acolyte" %% "jdbc-scala" % "1.0.32" % Test

name := "anorm-java8"

libraryDependencies ++= Seq(
  "specs2-core",
  "specs2-junit"
).map("org.specs2" %% _ % "2.4.9" % Test)

libraryDependencies += "com.chuusai" % "shapeless" % "2.0.0" %
Test cross CrossVersion.
  binaryMapped {
    case "2.10" => "2.10.4"
    case x => x
  }

libraryDependencies += "org.eu.acolyte" %% "jdbc-scala" % "1.0.32" % Test

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8.5")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
addSbtPlugin("com.typesafe.play" % "play-docs-sbt-plugin" % "2.4.0-M2")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")
// This release doesn't consider "requires" to be a keyword
libraryDependencies += "org.scalariform" %% "scalariform" % "0.1.5-20140822-69e2e30"


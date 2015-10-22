resolvers ++= DefaultOptions.resolvers(snapshot = true)

addSbtPlugin("com.typesafe.play" % "interplay" % "1.1.0")
addSbtPlugin("com.typesafe.play" % "play-docs-sbt-plugin" % sys.props.getOrElse("play.version", "2.5.0-M1"))
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.7")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")
// This release doesn't consider "requires" to be a keyword
resolvers += Resolver.typesafeRepo("releases")
libraryDependencies += "org.scalariform" %% "scalariform" % "0.1.5-20140822-69e2e30"


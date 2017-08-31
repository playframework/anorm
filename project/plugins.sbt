resolvers ++= DefaultOptions.resolvers(snapshot = true) :+ (
  Resolver.typesafeRepo("releases"))

addSbtPlugin("com.typesafe.play" % "interplay" % "1.3.5")
addSbtPlugin("com.typesafe.play" % "play-docs-sbt-plugin" % sys.props.getOrElse("play.version", "2.6.1"))

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.15")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.0")

addSbtPlugin("de.johoop" % "cpd4sbt" % "1.2.0")

addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.5")

resolvers ++= DefaultOptions.resolvers(snapshot = true) :+ (
  Resolver.typesafeRepo("releases"))

// Interplay is blocking SBT upgrade up to 1.0
addSbtPlugin("com.typesafe.play" % "interplay" % sys.props.get("interplay.version").getOrElse("2.0.2"))

addSbtPlugin("com.typesafe.play" % "play-docs-sbt-plugin" % sys.props.getOrElse("play.version", "2.6.15"))

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.18")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

//addSbtPlugin("de.johoop" % "cpd4sbt" % "1.2.0")

addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.5")

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.1")

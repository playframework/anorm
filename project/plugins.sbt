resolvers ++= DefaultOptions.resolvers(snapshot = true) :+ (
  Resolver.typesafeRepo("releases"))

addSbtPlugin("com.typesafe.play" % "interplay" % sys.props.get("interplay.version").getOrElse("2.0.8"))

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.4.0")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")

addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.9")

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.2")

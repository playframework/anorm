resolvers ++= DefaultOptions.resolvers(snapshot = true) ++ Seq(
  Resolver.typesafeRepo("releases"),
  "Tatami Releases" at "https://raw.github.com/cchantep/tatami/master/releases")

addSbtPlugin("com.typesafe.play" % "interplay" % sys.props.get("interplay.version").getOrElse("3.0.5"))

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.0")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")

addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.1.1")

addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.2.0")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")

addSbtPlugin("cchantep" % "sbt-scaladoc-compiler" % "0.2")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.34")

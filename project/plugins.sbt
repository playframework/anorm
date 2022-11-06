resolvers ++= DefaultOptions.resolvers(snapshot = true) ++ Seq(
  Resolver.typesafeRepo("releases"),
  "Tatami Releases".at("https://raw.github.com/cchantep/tatami/master/releases")
)

addSbtPlugin("com.typesafe.play" % "interplay" % sys.props.get("interplay.version").getOrElse("3.0.7"))

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.0")

addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.2.0")

addSbtPlugin("cchantep" % "sbt-scaladoc-compiler" % "0.2")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.3")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.11")

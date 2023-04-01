// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

resolvers ++= DefaultOptions.resolvers(snapshot = true) ++ Seq(
  Resolver.typesafeRepo("releases"),
  "Tatami Releases".at("https://raw.github.com/cchantep/tatami/master/releases")
)

addSbtPlugin("com.typesafe.play" % "interplay" % sys.props.get("interplay.version").getOrElse("3.0.7"))

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.2")

addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.1")

addSbtPlugin("cchantep" % "sbt-scaladoc-compiler" % "0.2")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.4")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.11")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.9.0")

// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

resolvers ++= DefaultOptions.resolvers(snapshot = true) ++ Seq(
  "Tatami Releases".at("https://raw.github.com/cchantep/tatami/master/releases")
)

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.6")

addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.1")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.4.1")

addSbtPlugin("cchantep" % "sbt-scaladoc-compiler" % "0.9")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.7")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.0")

addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")

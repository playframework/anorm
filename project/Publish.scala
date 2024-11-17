/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

import scala.xml.{ Elem => XmlElem, Node => XmlNode }

import sbt.Keys._
import sbt._

object Publish {
  val siteUrl = "https://playframework.github.io/anorm"

  lazy val settings = Seq(
    Test / publishArtifact := false,
    pomIncludeRepository   := { _ => false },
    autoAPIMappings        := true,
    apiURL                 := Some(url(s"$siteUrl/unidoc/anorm/")),
    pomPostProcess := transformPomDependencies { dep: XmlElem =>
      if ((dep \ "groupId").text == "com.sksamuel.scapegoat") {
        Option.empty[XmlElem] // discard
      } else Some(dep)
    },
    licenses := {
      Seq(
        "Apache 2.0" ->
          url("https://www.apache.org/licenses/LICENSE-2.0")
      )
    },
    homepage             := Some(url(siteUrl)),
    organizationName     := "The Play Framework Project",
    organizationHomepage := Some(url("https://playframework.com/")),
    developers := List(
      Developer(
        id = "playframework",
        name = "The Play Framework Contributors",
        email = "contact@playframework.com",
        url = url("https://github.com/playframework")
      )
    )
  )

  // ---

  private def transformPomDependencies(tx: XmlElem => Option[XmlNode]): XmlNode => XmlNode = { node: XmlNode =>
    import scala.xml.{ NodeSeq, XML }
    import scala.xml.transform.{ RewriteRule, RuleTransformer }

    val tr = new RuleTransformer(new RewriteRule {
      override def transform(node: XmlNode): NodeSeq = node match {
        case e: XmlElem if e.label == "dependency" =>
          tx(e) match {
            case Some(n) => n
            case _       => NodeSeq.Empty
          }

        case _ => node
      }
    })

    tr.transform(node).headOption match {
      case Some(transformed) => transformed
      case _                 => sys.error("Fails to transform the POM")
    }
  }
}

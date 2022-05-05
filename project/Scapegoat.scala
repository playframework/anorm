import sbt.Keys._
import sbt._

import com.sksamuel.scapegoat.sbt.ScapegoatSbtPlugin

object Scapegoat {
  import ScapegoatSbtPlugin.autoImport._

  val settings = Seq(
    ThisBuild / scapegoatVersion := "1.4.13",
    ThisBuild / scapegoatReports := Seq("text"),
    ThisBuild / scapegoatDisabledInspections := Seq(
      "FinalModifierOnCaseClass"),
    pomPostProcess := transformPomDependencies { dep =>
      if ((dep \ "groupId").text == "com.sksamuel.scapegoat") {
        None
      } else Some(dep)
    })

  import scala.xml.{ Elem => XmlElem, Node => XmlNode }
  private def transformPomDependencies(tx: XmlElem => Option[XmlNode]): XmlNode => XmlNode = { node: XmlNode =>
    import scala.xml.{ NodeSeq, XML }
    import scala.xml.transform.{ RewriteRule, RuleTransformer }

    val tr = new RuleTransformer(new RewriteRule {
      override def transform(node: XmlNode): NodeSeq = node match {
        case e: XmlElem if e.label == "dependency" => tx(e) match {
          case Some(n) => n
          case _ => NodeSeq.Empty
        }

        case _ => node
      }
    })

    tr.transform(node).headOption match {
      case Some(transformed) => transformed
      case _ => sys.error("Fails to transform the POM")
    }
  }
}

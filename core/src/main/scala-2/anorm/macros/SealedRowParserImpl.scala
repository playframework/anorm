/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.macros

import scala.reflect.macros.whitebox

import anorm.Macro.{ debugEnabled, Discriminate, DiscriminatorNaming }
import anorm.RowParser
import anorm.macros.Inspect.{ directKnownSubclasses, pretty }

private[anorm] object SealedRowParserImpl {
  def apply[T: c.WeakTypeTag](
      c: whitebox.Context
  )(naming: c.Expr[DiscriminatorNaming], discriminate: c.Expr[Discriminate]): c.Expr[RowParser[T]] = {
    import c.universe._

    val tpe = c.weakTypeTag[T].tpe

    @inline def abort(msg: String) = c.abort(c.enclosingPosition, msg)
    val sub = directKnownSubclasses(c)(tpe).filter { subclass =>
      if (subclass.typeSymbol.asClass.typeParams.nonEmpty) {
        c.warning(c.enclosingPosition, s"class with type parameters is not supported as family member: $subclass")

        false
      } else true
    }

    if (sub.isEmpty) {
      abort(s"cannot find any subclass: $tpe")
    }

    val parserTpe = c.weakTypeTag[RowParser[_]].tpe
    val missing: List[Type] = sub.flatMap { subclass =>
      val ptype = appliedType(parserTpe, List(subclass))

      c.inferImplicitValue(ptype) match {
        case EmptyTree => List(subclass)
        case _         => List.empty
      }
    }

    if (missing.nonEmpty) {
      def details = missing
        .map { subclass =>
          val typeStr = if (subclass.typeSymbol.companion == NoSymbol) {
            s"${subclass.typeSymbol.fullName}.type"
          } else subclass.typeSymbol.fullName

          s"- cannot find anorm.RowParser[$typeStr] in the implicit scope"
        }
        .mkString(",\r\n")

      abort(s"fails to generate sealed parser: $tpe;\r\n$details")
    }

    // ---

    val cases = sub.map { subclass =>
      val caseName = TermName(c.freshName("discriminated"))
      val key      = q"$discriminate(${subclass.typeSymbol.fullName})"
      val caseDecl = q"val $caseName = $key"

      (key, caseDecl, cq"`$caseName` => implicitly[anorm.RowParser[$subclass]]")
    }

    lazy val supported = q"List(..${cases.map(_._1)})"
    def mappingError =
      q"""anorm.RowParser.failed[$tpe](anorm.Error(anorm.SqlMappingError("unexpected row type '%s'; expected: %s".format(d, $supported))))"""

    val discriminatorTerm = TermName(c.freshName("discriminator"))
    val colTerm           = TermName(c.freshName("column"))

    @SuppressWarnings(Array("ListAppend" /* only once*/ ))
    def matching = Match(q"$discriminatorTerm", cases.map(_._3) :+ cq"d => $mappingError")

    val parser = q"""new anorm.RowParser[$tpe] {
      val $colTerm = $naming(${tpe.typeSymbol.fullName})
      val underlying: anorm.RowParser[$tpe] = 
        anorm.SqlParser.str($colTerm).flatMap { $discriminatorTerm: String => 
          ..${cases.map(_._2) :+ matching}
        }

      def apply(row: Row): anorm.SqlResult[$tpe] = underlying(row)
    }"""

    if (debugEnabled) {
      c.echo(c.enclosingPosition, s"row parser generated for $tpe: ${pretty(c)(parser)}")
    }

    c.Expr[RowParser[T]](c.typecheck(parser))
  }
}

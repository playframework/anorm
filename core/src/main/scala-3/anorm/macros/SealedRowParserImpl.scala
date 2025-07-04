/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.macros

import scala.quoted.{ Expr, Quotes, Type }

import anorm.{ Error, RowParser, SqlMappingError, SqlParser }
import anorm.Macro.{ debugEnabled, Discriminate, DiscriminatorNaming }

private[anorm] object SealedRowParserImpl {
  def apply[A](
      naming: Expr[DiscriminatorNaming],
      discriminate: Expr[Discriminate]
  )(using q: Quotes, tpe: Type[A]): Expr[RowParser[A]] = {
    import q.reflect.*

    val repr = TypeRepr.of[A](using tpe)

    val subclasses = Inspect.knownSubclasses(q)(repr) match {
      case Some(classes) =>
        classes

      case None =>
        report.errorAndAbort(s"cannot find any subclass: ${repr.show}")
    }

    // ---

    type CaseType[U <: A] = U

    val subParsers = List.newBuilder[(TypeRepr, Expr[RowParser[_]])]

    val missing: List[TypeRepr] = subclasses.flatMap { subcls =>
      subcls.asType match {
        case '[CaseType[sub]] =>
          Expr.summon[RowParser[sub]] match {
            case Some(subParser) => {
              subParsers += subcls -> subParser

              List.empty[TypeRepr]
            }

            case _ =>
              List(subcls)
          }

        case _ =>
          List(subcls)
      }
    }

    if (missing.nonEmpty) {
      def details = missing
        .map { subcls =>
          s"- cannot find anorm.RowParser[${subcls.show}] in the implicit scope"
        }
        .mkString(",\r\n")

      report.errorAndAbort(s"fails to generate sealed parser: ${repr.show};\r\n$details")
    }

    // ---

    val cases: List[(String, CaseDef)] = subParsers.result().map {
      case (subcls, subParser) =>
        val tpeSym  = subcls.typeSymbol
        val tpeName = {
          if (tpeSym.flags.is(Flags.Module)) tpeSym.fullName.stripSuffix(f"$$")
          else tpeSym.fullName
        }

        val key = '{ $discriminate(${ Expr(tpeName) }) }

        val bind =
          Symbol.newBind(
            Symbol.spliceOwner,
            tpeSym.name,
            Flags.Case,
            TypeRepr.of[String]
          )

        val ref = Ref(bind).asExprOf[String]

        tpeSym.fullName -> CaseDef(
          Bind(bind, Wildcard()),
          guard = Some('{ $ref == $key }.asTerm),
          rhs = subParser.asTerm
        )
    }

    def fallbackCase: CaseDef = {
      val fallbackBind =
        Symbol.newBind(
          Symbol.spliceOwner,
          "d",
          Flags.Case,
          TypeRepr.of[String]
        )

      val fallbackVal = Ref(fallbackBind).asExprOf[String]

      CaseDef(
        Bind(fallbackBind, Wildcard()),
        guard = None,
        rhs = '{
          val msg =
            "unexpected row type '%s'; expected: %s".format($fallbackVal, ${ Expr(cases.map(_._1)) }.mkString(", "))

          RowParser.failed[A](Error(SqlMappingError(msg)))
        }.asTerm
      )
    }

    inline def body(inline discriminatorVal: Expr[String]): Expr[RowParser[A]] =
      Match(
        discriminatorVal.asTerm,
        cases.map(_._2) :+ fallbackCase
      ).asExprOf[RowParser[A]]

    val parser: Expr[RowParser[A]] = '{
      val discriminatorCol = $naming(${ Expr(repr.typeSymbol.fullName) })

      SqlParser.str(discriminatorCol).flatMap { (discriminatorVal: String) =>
        ${ body('discriminatorVal) }
      }
    }

    if (debugEnabled) {
      report.info(s"row parser generated for $tpe: ${parser.show}")
    }

    parser
  }
}

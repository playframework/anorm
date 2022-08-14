package anorm.macros

import scala.quoted.{ Expr, Quotes, Type }

import anorm.Macro.debugEnabled
import anorm.ToStatement

private[anorm] object ValueToStatement {
  def apply[A <: AnyVal](
    using q: Quotes, tpe: Type[A]): Expr[ToStatement[A]] = {

    import q.reflect.*

    val aTpr  = TypeRepr.of[A](using tpe)
    val ctor      = aTpr.typeSymbol.primaryConstructor

    ctor.paramSymss match {
      case List(v) :: Nil => {
        /* TODO: Remove;
        val resolveImplicit = ImplicitResolver[A](q).resolver[Column, T](
          forwardExpr, Map.empty, debug = report.info(_))
         */

        v.tree match {
          case vd: ValDef => {
            val tpr = vd.tpt.tpe

            tpr.asType match {
              case vtpe @ '[t] =>
                Expr.summon[ToStatement[t]] match {
                  case Some(ts) => {
                    def inner(a: Expr[A]) = {
                      val term = asTerm(a)

                      term
                        .select(term.symbol.fieldMember(v.name))
                        .asExprOf[t](using vtpe)
                    }

                    val generated = '{
                      new ToStatement[A] {
                        def set(
                          s: java.sql.PreparedStatement, i: Int, a: A): Unit =
                          ${ts}.set(s, i, ${inner('a)})
                      }
                    }

                    if (debugEnabled) {
                      report.info(s"ToStatement for $tpe: ${generated.show}")
                    }

                    generated
                  }

                  case _ =>
                    report.errorAndAbort(s"Instance not found: ${classOf[ToStatement[_]].getName}[${tpr.typeSymbol.fullName}]")
                }

            }
          }

          case _ =>
            report.errorAndAbort(
              s"Constructor parameter expected, found: ${v}"
            )
        }
      }

      case _ =>
        report.errorAndAbort(
          s"Cannot resolve value ToStatement for '${aTpr.typeSymbol.name}'"
        )
    }
  }
}

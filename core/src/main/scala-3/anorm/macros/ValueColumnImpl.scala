package anorm.macros

import scala.quoted.{ Expr, Quotes, Type }

import anorm.Column
import anorm.Macro.debugEnabled

private[anorm] trait ValueColumn {
  protected def valueColumnImpl[A <: AnyVal](using q: Quotes, tpe: Type[A]): Expr[Column[A]] = {
    import q.reflect.*

    val aTpr = TypeRepr.of[A](using tpe)
    val ctor = aTpr.typeSymbol.primaryConstructor

    ctor.paramSymss match {
      case List(v) :: Nil => {
        v.tree match {
          case vd: ValDef => {
            val tpr = vd.tpt.tpe

            tpr.asType match {
              case vtpe @ '[t] =>
                Expr.summon[Column[t]] match {
                  case Some(col) => {
                    def mapf(in: Expr[t]): Expr[A] =
                      New(Inferred(aTpr))
                        .select(ctor)
                        .appliedTo(in.asTerm)
                        .asExprOf[A]

                    val generated = '{ ${ col }.map(in => ${ mapf('in) }) }

                    if (debugEnabled) {
                      report.info(s"column generated for ${aTpr.show}: ${generated.show}")
                    }

                    generated
                  }

                  case _ =>
                    report.errorAndAbort(
                      s"Instance not found: ${classOf[Column[_]].getName}[${tpr.typeSymbol.fullName}]"
                    )
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
          s"Cannot resolve value Column for '${aTpr.typeSymbol.name}'"
        )
    }
  }
}

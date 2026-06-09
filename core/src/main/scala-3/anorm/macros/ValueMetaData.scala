/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.macros

import scala.quoted.{ Expr, Quotes, Type }

import anorm.Macro.debugEnabled
import anorm.ParameterMetaData

private[anorm] trait ValueMetaData {
  protected def valueParameterMetaDataImpl[A <: AnyVal](using q: Quotes, tpe: Type[A]): Expr[ParameterMetaData[A]] = {
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
                Expr.summon[ParameterMetaData[t]] match {
                  case Some(meta) => {
                    def mapf(in: Expr[t]): Expr[A] =
                      New(Inferred(aTpr))
                        .select(ctor)
                        .appliedTo(in.asTerm)
                        .asExprOf[A]

                    val generated = '{
                      new _root_.anorm.ParameterMetaData[A] {
                        val underlying = ${ meta }
                        val sqlType    = underlying.sqlType
                        val jdbcType   = underlying.jdbcType
                      }
                    }

                    if (debugEnabled) {
                      report.info(s"meta data generated for ${aTpr.show}: ${generated.show}")
                    }

                    generated
                  }

                  case _ =>
                    report.errorAndAbort(
                      s"Instance not found: ${classOf[ParameterMetaData[?]].getName}[${tpr.typeSymbol.fullName}]"
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
          s"Cannot resolve value ParameterMetaData for '${aTpr.typeSymbol.name}'"
        )
    }
  }
}

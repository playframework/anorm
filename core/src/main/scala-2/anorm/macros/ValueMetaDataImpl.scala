/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.macros

import scala.reflect.macros.whitebox

import anorm.Macro.debugEnabled
import anorm.ParameterMetaData
import anorm.macros.Inspect.pretty

private[anorm] object ValueMetaDataImpl {
  def apply[T <: AnyVal: c.WeakTypeTag](c: whitebox.Context): c.Expr[ParameterMetaData[T]] = {
    @inline def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    val tpe       = c.weakTypeTag[T].tpe
    val ctor      = tpe.decl(c.universe.termNames.CONSTRUCTOR).asMethod
    val anyValTpe = c.typeOf[AnyVal]

    import c.universe._

    ctor.paramLists match {
      case List(term: TermSymbol) :: Nil => {
        val metaTpe = c.weakTypeTag[ParameterMetaData[_]].tpe

        if (!(term.info <:< anyValTpe)) {
          abort(s"value class must only have a single value parameter: ${term.info} does not extend AnyVal")
        } else {
          val forwardName                                                       = TermName(c.freshName("forward"))
          val resolveImplicit: (Name, Type, Type) => Implicit[Type, Name, Tree] =
            ImplicitResolver(c)(tpe, Map.empty, forwardName)

          // Try to resolve `ParameterMetaData[..]`
          resolveImplicit(term.name, term.info, metaTpe) match {
            case Implicit.Unresolved() => { // No `ParameterMetaData[..]` ...
              abort(s"cannot find $metaTpe for ${term.name}:${term.info} in $ctor")
            }

            case Implicit(_, _, meta, _, _) => {
              val generated = q"""new _root_.anorm.ParameterMetaData[${tpe}] {
                val underlying = ${meta}
                val sqlType = underlying.sqlType
                val jdbcType = underlying.jdbcType
              }"""

              if (debugEnabled) {
                c.echo(c.enclosingPosition, s"meta data generated for $tpe: ${pretty(c)(generated)}")
              }

              c.Expr[ParameterMetaData[T]](c.typecheck(generated))
            }
          }
        }
      }

      case _ => abort(s"constructor for a value class must have a single argument: $ctor")
    }
  }
}

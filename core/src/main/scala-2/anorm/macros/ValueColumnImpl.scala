/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.macros

import scala.reflect.macros.whitebox

import anorm.Column
import anorm.Macro.debugEnabled
import anorm.macros.Inspect.pretty

private[anorm] object ValueColumnImpl {
  def apply[T <: AnyVal: c.WeakTypeTag](c: whitebox.Context): c.Expr[Column[T]] = {
    @inline def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    val tpe       = c.weakTypeTag[T].tpe
    val ctor      = tpe.decl(c.universe.termNames.CONSTRUCTOR).asMethod
    val anyValTpe = c.typeOf[AnyVal]

    import c.universe._

    ctor.paramLists match {
      case List(term: TermSymbol) :: Nil => {
        val colTpe = c.weakTypeTag[Column[_]].tpe

        val forwardName = TermName(c.freshName("forward"))
        val resolveImplicit: (Name, Type, Type) => Implicit[Type, Name, Tree] =
          ImplicitResolver(c)(tpe, Map.empty, forwardName)

        if (!(term.info <:< anyValTpe)) {
          abort(s"value class must only have a single value parameter: ${term.info} does not extend AnyVal")
        } else {
          // Try to resolve `Column[..]`
          resolveImplicit(term.name, term.info, colTpe) match {
            case Implicit.Unresolved() => { // No `Column[..]` ...
              abort(s"cannot find $colTpe for ${term.name}:${term.info} in $ctor")
            }

            case Implicit(_, _, col, _, _) => {
              val generated = q"""${col}.map[$tpe] { 
                v: ${term.info} => new ${tpe}(v) 
              }"""

              if (debugEnabled) {
                c.echo(c.enclosingPosition, s"column generated for $tpe: ${pretty(c)(generated)}")
              }

              c.Expr[Column[T]](generated)
            }
          }
        }
      }

      case _ => abort(s"constructor for a value class must have a single argument: $ctor")
    }
  }
}

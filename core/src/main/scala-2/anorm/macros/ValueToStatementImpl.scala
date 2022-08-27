package anorm.macros

import scala.reflect.macros.whitebox

import anorm.Macro.debugEnabled
import anorm.ToStatement
import anorm.macros.Inspect.pretty

private[anorm] object ValueToStatement {
  def apply[T <: AnyVal: c.WeakTypeTag](c: whitebox.Context): c.Expr[ToStatement[T]] = {
    @inline def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    val tpe  = c.weakTypeTag[T].tpe
    val ctor = tpe.decl(c.universe.termNames.CONSTRUCTOR).asMethod

    import c.universe._

    ctor.paramLists match {
      case List(term: TermSymbol) :: Nil => {
        val tsTpe = c.weakTypeTag[ToStatement[_]].tpe

        val forwardName = TermName(c.freshName("forward"))
        val resolveImplicit: (Name, Type, Type) => Implicit[Type, Name, Tree] =
          ImplicitResolver(c)(tpe, Map.empty, forwardName)

        // Try to resolve `ToStatement[..]`
        resolveImplicit(term.name, term.info, tsTpe) match {
          case Implicit.Unresolved() => { // No `ToStatement[..]` ...
            abort(s"cannot find $tsTpe for ${term.name}:${term.info} in $ctor")
          }

          case Implicit(_, _, toStmt, _, _) => {
            val generated = q"""new _root_.anorm.ToStatement[$tpe] {
              def set(s: java.sql.PreparedStatement, i: Int, v: $tpe): Unit = 
                ${toStmt}.set(s, i, v.${term.name})
            }"""

            if (debugEnabled) {
              c.echo(c.enclosingPosition, s"ToStatement for $tpe: ${pretty(c)(generated)}")
            }

            c.Expr[ToStatement[T]](generated)
          }
        }
      }

      case _ =>
        abort(s"cannot supported ${show(ctor)} for ${tpe}")
    }
  }
}

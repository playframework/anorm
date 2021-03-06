package anorm.macros

import scala.reflect.macros.whitebox

import anorm.{ Column, RowParser }
import anorm.Macro.debugEnabled
import anorm.macros.Inspect.pretty

private[anorm] object RowParserImpl {
  def apply[T: c.WeakTypeTag](c: whitebox.Context)(genGet: (c.universe.Type, String, Int) => c.universe.Tree): c.Expr[T] = {
    val tpe = c.weakTypeTag[T].tpe
    @inline def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    if (!tpe.typeSymbol.isClass || !tpe.typeSymbol.asClass.isCaseClass) {
      abort(s"case class expected: $tpe")
    }

    val ctor = tpe.decl(c.universe.termNames.CONSTRUCTOR).asMethod

    if (ctor.paramLists.isEmpty) {
      abort(s"parsed data cannot be passed as parameter: $ctor")
    }

    val colTpe = c.weakTypeTag[Column[_]].tpe
    val parserTpe = c.weakTypeTag[RowParser[_]].tpe

    import c.universe._

    val boundTypes: Map[String, Type] = Inspect.boundTypes(c)(tpe)
    val forwardName = TermName(c.freshName("forward"))

    val resolveImplicit: (Name, Type, Type) => Implicit[Type, Name, Tree] =
      ImplicitResolver(c)(tpe, boundTypes, forwardName)

    // ---

    val (x, m, body, _, hasSelfRef) = ctor.paramLists.foldLeft[(Tree, Tree, Tree, Int, Boolean)]((EmptyTree, EmptyTree, EmptyTree, 0, false)) {
      case ((xa, ma, bs, ia, sr), pss) =>
        val (xb, mb, vs, ib, selfRef) =
          pss.foldLeft((xa, ma, List.empty[Tree], ia, sr)) {
            case ((xtr, mp, ps, pi, sref), term: TermSymbol) => {
              val tn = term.name.toString
              val tt = {
                val t = term.typeSignature

                boundTypes.getOrElse(t.typeSymbol.fullName, t)
                // TODO: term.isParamWithDefault
              }

              // Try to resolve `Column[tt]`
              resolveImplicit(term.name, tt, colTpe) match {
                case Implicit.Unresolved() => // No `Column[tt]` ...
                  // ... try to resolve `RowParser[tt]`
                  resolveImplicit(term.name, tt, parserTpe) match {
                    case Implicit.Unresolved() => abort(s"cannot find $colTpe nor $parserTpe for ${term.name}:$tt in $ctor")

                    case Implicit(_, _, pr, _, s) => {
                      // Use an existing `RowParser[T]` as part
                      pq"${term.name}" match {
                        case b @ Bind(bn, _) => {
                          val bt = q"${bn.toTermName}"

                          xtr match {
                            case EmptyTree =>
                              (pr, b, List[Tree](bt), pi + 1, s || sref)

                            case _ => (q"$xtr ~ $pr",
                              pq"anorm.~($mp, $b)", bt :: ps, pi + 1, s || sref)

                          }
                        }

                        case _ =>
                          abort(s"unsupported $colTpe nor $parserTpe for ${term.name}:$tt in $ctor")
                      }
                    }
                  }

                case Implicit(_, _, itree, _, _) => {
                  // Generate a `get` for the `Column[T]`
                  val get = genGet(tt, tn, pi)

                  pq"${term.name}" match {
                    case b @ Bind(bn, _) => {
                      val bt = q"${bn.toTermName}"

                      xtr match {
                        case EmptyTree =>
                          (get, b, List[Tree](bt), pi + 1, sref)

                        case _ => (q"$xtr ~ $get($itree)",
                          pq"anorm.~($mp, $b)", bt :: ps, pi + 1, sref)

                      }
                    }

                    case _ =>
                      abort(s"unsupported $colTpe nor $parserTpe for ${term.name}:$tt: ${show(itree)}")
                  }
                }
              }
            }

            case (state, sym) => {
              c.warning(c.enclosingPosition, s"unexpected symbol: $sym")
              state
            }
          }

        val by = bs match {
          case EmptyTree => q"new $tpe(..${vs.reverse})"
          case xs => q"$xs(..${vs.reverse})"
        }

        (xb, mb, by, ib, selfRef)
    }

    val caseDef = cq"$m => { $body }"
    val patMat = q"$x.map[$tpe] { _ match { case $caseDef } }"
    val parser = if (!hasSelfRef) patMat else {
      val generated = TypeName(c.freshName("Generated"))
      val rowParser = TermName(c.freshName("rowParser"))

      q"""{
        final class $generated() {
          val ${forwardName} = 
            anorm.RowParser[$tpe]($rowParser)

          def $rowParser: anorm.RowParser[$tpe] = $patMat
        }

        new $generated().$rowParser
      }"""
    }

    if (debugEnabled) {
      c.echo(c.enclosingPosition, s"row parser generated for $tpe: ${pretty(c)(parser)}")
    }

    c.Expr(c.typecheck(parser))
  }
}

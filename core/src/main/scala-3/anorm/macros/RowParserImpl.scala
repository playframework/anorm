/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.macros

import scala.quoted.{ Expr, Quotes, Type }

import anorm.{ ~, Column, Row, RowParser, SqlResult }
import anorm.Macro.{ debugEnabled, RowParserGenerator }

private[anorm] object RowParserImpl {
  def apply[A](
      q: Quotes,
      forwardExpr: Expr[RowParser[A]]
  )(genGet: RowParserGenerator)(using tpe: Type[A], parserTpe: Type[RowParser]): Expr[Row => SqlResult[A]] = {
    given quotes: Quotes = q

    import q.reflect.*

    val (repr, erased, aTArgs) = TypeRepr.of[A](using tpe) match {
      case tpr @ AppliedType(e, args) =>
        Tuple3(
          tpr,
          e,
          args.collect {
            case repr: TypeRepr =>
              repr
          }
        )

      case tpr =>
        Tuple3(tpr, tpr, List.empty[TypeRepr])
    }

    @inline def abort(msg: String) = report.errorAndAbort(msg)

    val tpeSym = repr.typeSymbol

    if (!tpeSym.isClassDef || !tpeSym.flags.is(Flags.Case)) {
      abort(s"case class expected: ${repr.show}")
    }

    // ---

    val ctor = tpeSym.primaryConstructor

    val (boundTypes, properties) = ctor.paramSymss match {
      case targs :: paramss if targs.forall(_.isType) && paramss.headOption.exists(_.nonEmpty) => {
        val boundTps = targs.zip(aTArgs).toMap

        boundTps -> paramss
      }

      case params :: Nil if !params.exists(_.isType) =>
        Map.empty[Symbol, TypeRepr] -> List(params)

      case _ =>
        report.errorAndAbort(s"${repr.show} constructor has no parameter")
    }

    if (properties.isEmpty) {
      abort(s"parsed data cannot be passed as parameter: $ctor")
    }

    val debug = {
      if (debugEnabled) report.info(_: String)
      else (_: String) => {}
    }

    val resolv = ImplicitResolver[A](q).resolver(forwardExpr, Map.empty, debug)(parserTpe)

    // ---

    /*
     * @tparam T the type of parsed data (single column or tuple-like `~`)
     * @param parsing the parsing expression (e.g. `get("a") ~ get("b")`)
     * @param parsedTpr the representation of type `T`
     * @param matchPattern the match pattern to extract values inside `.map`
     * @param columnSymss the column symbols bounds in the `matchPattern` (list of list as properties can be passed as multiple parameter list to the constructor)
     */
    case class GenerationState[T](
        parsing: Expr[RowParser[T]],
        parsedTpr: TypeRepr,
        matchPattern: Tree,
        columnSymss: List[List[Symbol]]
    )

    val pkg = Symbol.requiredPackage("anorm")
    val TildeSelect = (for {
      ts <- pkg.declaredType("~").headOption.map(_.companionModule)
      un <- ts.declaredMethod("unapply").headOption
    } yield Ref(pkg).select(ts).select(un)) match {
      case Some(select) =>
        select

      case _ =>
        abort("Fails to resolve ~ symbol")
    }

    @annotation.tailrec
    def prepare[T](
        propss: List[List[Symbol]],
        pi: Int,
        combined: Option[GenerationState[T]],
        hasSelfRef: Boolean,
        hasGenericProperty: Boolean
    )(using Type[T]): Option[Expr[Row => SqlResult[A]]] =
      propss.headOption match {
        case Some(sym :: localTail) => {
          val tn = sym.name

          val tt: TypeRepr = sym.tree match {
            case vd: ValDef => {
              val vtpe = vd.tpt.tpe

              boundTypes.getOrElse(vtpe.typeSymbol, vtpe)
            }

            case _ =>
              abort(s"Value definition expected for ${repr.show} constructor parameter: $sym")
          }

          val isGenericProp = tt match {
            case AppliedType(_, as) =>
              as.nonEmpty

            case _ =>
              false
          }

          val colSym: Symbol =
            Symbol.newBind(Symbol.spliceOwner, tn, Flags.Case, tt)

          // Pattern to match a single column in `.map` pattern matching
          val singlePat = Bind(colSym, Typed(Wildcard(), Inferred(tt)))

          tt.asType match {
            case '[t] =>
              def initialState(expr: Expr[RowParser[t]]) =
                GenerationState[t](
                  parsing = expr,
                  parsedTpr = tt,
                  matchPattern = singlePat,
                  columnSymss = List(colSym) :: Nil
                )

              def combineState(
                  parent: GenerationState[T],
                  expr: Expr[RowParser[T ~ t]]
              ): GenerationState[T ~ t] = {
                val pat = Unapply(
                  fun = TypeApply(TildeSelect, List(Inferred(parent.parsedTpr), Inferred(tt))),
                  implicits = Nil,
                  patterns = List(parent.matchPattern, singlePat)
                )

                GenerationState[T ~ t](
                  parsing = expr,
                  parsedTpr = TypeRepr.of[T ~ t],
                  matchPattern = pat,
                  columnSymss = parent.columnSymss match {
                    case headList :: tails =>
                      (colSym :: headList) :: tails

                    case _ => {
                      // Should have been handled by initialState, smth wrong
                      abort(s"Fails to handle initial state of RowParser generation: ${repr.show}")
                    }
                  }
                )
              }

              Expr.summon[Column[t]] match {
                case None =>
                  // ... try to resolve `RowParser[tt]`
                  resolv(tt) match {
                    case None =>
                      abort(s"cannot find Column nor RowParser for ${tn}:${tt.show} in ${ctor.fullName}")

                    case Some((pr, s)) => {
                      val hasSelf = if s then s else hasSelfRef

                      // Use an existing `RowParser[t]` as part
                      val tpr: Expr[RowParser[t]] = pr.asExprOf[RowParser[t]]

                      combined match {
                        case Some(parent @ GenerationState(prev, _, _, _)) =>
                          prepare[T ~ t](
                            propss = localTail :: propss.tail,
                            pi = pi + 1,
                            combined = Some {
                              combineState(parent, '{ $prev ~ $tpr })
                            },
                            hasSelfRef = hasSelf,
                            hasGenericProperty = isGenericProp || hasGenericProperty
                          )

                        case _ =>
                          prepare[t](
                            propss = localTail :: propss.tail,
                            pi = pi + 1,
                            combined = Some(initialState(tpr)),
                            hasSelfRef = hasSelf,
                            hasGenericProperty = isGenericProp || hasGenericProperty
                          )
                      }
                    }
                  }

                case Some(col) => {
                  // Generate a `get` for the `Column[T]`
                  val get: Expr[RowParser[t]] = genGet[t](col, tn, pi)

                  combined match {
                    case Some(parent @ GenerationState(prev, _, _, _)) =>
                      prepare[T ~ t](
                        propss = localTail :: propss.tail,
                        pi = pi + 1,
                        combined = Some {
                          combineState(parent, '{ $prev ~ $get })
                        },
                        hasSelfRef = hasSelfRef,
                        hasGenericProperty = isGenericProp || hasGenericProperty
                      )

                    case None =>
                      prepare[t](
                        propss = localTail :: propss.tail,
                        pi = pi + 1,
                        combined = Some(initialState(get)),
                        hasSelfRef = hasSelfRef,
                        hasGenericProperty = isGenericProp || hasGenericProperty
                      )
                  }
                }
              }
          }
        }

        case Some(Nil) if propss.tail.nonEmpty => {
          // End of one parameter list for the properties, but there is more

          prepare[T](
            propss = propss.tail, // other parameter lists
            pi = pi,
            combined = combined match {
              case Some(state) =>
                Some(state.copy(columnSymss = Nil :: state.columnSymss))

              case None =>
                abort("Missing generation state: ${repr.show}")
            },
            hasSelfRef = hasSelfRef,
            hasGenericProperty = hasGenericProperty
          )
        }

        case Some(Nil) | None =>
          combined match {
            case None =>
              None

            case Some(GenerationState(parsing, _, matchPattern, revColss)) => {
              val targs = boundTypes.values.toList
              val colArgss = revColss.reverse.map {
                _.reverse.map(Ref(_: Symbol))
              }
              val newTerm = New(Inferred(erased)).select(ctor)

              val ctorCall: Expr[A] = {
                if (targs.nonEmpty) {
                  newTerm.appliedToTypes(targs).appliedToArgss(colArgss)
                } else {
                  newTerm.appliedToArgss(colArgss)
                }
              }.asExprOf[A]

              val ctorCase = CaseDef(
                pattern = matchPattern,
                guard = None,
                rhs = '{ anorm.Success[A](${ ctorCall }) }.asTerm
              )

              inline def cases[U: Type](inline parsed: Expr[U]) = {
                // Workaround as in case of generic property
                // (whose type has type arguments), false warning is raised
                // about exhaustivity.

                List(
                  ctorCase,
                  CaseDef(
                    Wildcard(),
                    guard = None,
                    rhs = '{
                      anorm.Error(
                        anorm.SqlMappingError(
                          "Unexpected parsed value: " + ${ parsed }
                        )
                      )
                    }.asTerm
                  )
                )
              }

              inline def flatMapParsed[U: Type](inline parsed: Expr[U]): Expr[SqlResult[A]] =
                Match(parsed.asTerm, cases(parsed)).asExprOf[SqlResult[A]]

              Some('{
                lazy val parsingRow = ${ parsing }

                { (row: Row) =>
                  parsingRow(row).flatMap { parsed =>
                    ${ flatMapParsed('parsed) }
                  }
                }
              })
            }
          }
      }

    val generated: Expr[Row => SqlResult[A]] =
      prepare[Nothing](properties, 0, None, false, false) match {
        case Some(fn) =>
          fn

        case _ =>
          abort(s"Fails to prepare the parser function: ${repr.show}")
      }

    debug(s"row parser generated for ${repr.show}: ${generated.show}")

    generated
  }
}

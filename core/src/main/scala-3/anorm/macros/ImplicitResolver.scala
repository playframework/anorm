package anorm.macros

import scala.quoted.{ Expr, Quotes, Type }

import anorm.Macro.Placeholder

private[macros] trait ImplicitResolver[A, Q <: Quotes] {
  protected val quotes: Q

  import quotes.reflect.*

  // format: off
  private given q: Q = quotes
  // format: on

  protected val aTpeRepr: TypeRepr

  // The placeholder type
  protected final lazy val PlaceholderType: TypeRepr =
    TypeRepr.of[Placeholder]

  /**
   * Refactor the input types, by replacing any type matching the `filter`,
   * by the given `replacement`.
   */
  @annotation.tailrec
  private def refactor(
      in: List[TypeRepr],
      base: (TypeRepr, /*Type*/ Symbol),
      out: List[TypeRepr],
      tail: List[
        (List[TypeRepr], (TypeRepr, /*Type*/ Symbol), List[TypeRepr])
      ],
      filter: TypeRepr => Boolean,
      replacement: TypeRepr,
      altered: Boolean
  ): (TypeRepr, Boolean) = in match {
    case tpe :: ts =>
      tpe match {
        case t if filter(t) =>
          refactor(
            ts,
            base,
            replacement :: out,
            tail,
            filter,
            replacement,
            true
          )

        case AppliedType(t, as) if as.nonEmpty =>
          refactor(
            as,
            t -> t.typeSymbol,
            List.empty,
            (ts, base, out) :: tail,
            filter,
            replacement,
            altered
          )

        case t =>
          refactor(
            ts,
            base,
            t :: out,
            tail,
            filter,
            replacement,
            altered
          )
      }

    case _ => {
      val tpe = base._1.appliedTo(out.reverse)

      tail match {
        case (x, y, more) :: ts =>
          refactor(
            x,
            y,
            tpe :: more,
            ts,
            filter,
            replacement,
            altered
          )

        case _ => tpe -> altered
      }
    }
  }

  /**
   * Replaces any reference to the type itself by the Placeholder type.
   * @return the normalized type + whether any self reference has been found
   */
  private def normalized(tpe: TypeRepr): (TypeRepr, Boolean) =
    tpe match {
      case t if t =:= aTpeRepr => PlaceholderType -> true

      case AppliedType(t, args) if args.nonEmpty =>
        refactor(
          args,
          t -> t.typeSymbol,
          List.empty,
          List.empty,
          _ =:= aTpeRepr,
          PlaceholderType,
          false
        )

      case t => t -> false
    }

  /* Restores reference to the type itself when Placeholder is found. */
  private def denormalized(ptype: TypeRepr): TypeRepr = ptype match {
    case t if t =:= PlaceholderType =>
      aTpeRepr

    case AppliedType(base, args) if args.nonEmpty =>
      refactor(
        args,
        base -> base.typeSymbol,
        List.empty,
        List.empty,
        _ == PlaceholderType,
        aTpeRepr,
        false
      )._1

    case _ => ptype
  }

  private val PlaceholderHandlerName =
    "reactivemongo.api.bson.Macros.Placeholder.Handler"

  /**
   * @param tc the type representation of the typeclass
   * @param forwardExpr the `Expr` that forward to the materialized instance itself
   */
  private class ImplicitTransformer[T](forwardExpr: Expr[T]) extends TreeMap {
    private val denorm = denormalized _

    @SuppressWarnings(Array("AsInstanceOf"))
    override def transformTree(tree: Tree)(owner: Symbol): Tree = tree match {
      case TypeApply(tpt, args) =>
        TypeApply(
          transformTree(tpt)(owner).asInstanceOf[Term],
          args.map(transformTree(_)(owner).asInstanceOf[TypeTree])
        )

      case t @ (Select(_, _) | Ident(_)) if t.show == PlaceholderHandlerName =>
        forwardExpr.asTerm

      case tt: TypeTree =>
        super.transformTree(
          TypeTree.of(using denorm(tt.tpe).asType)
        )(owner)

      case Apply(fun, args) =>
        Apply(
          transformTree(fun)(owner).asInstanceOf[Term],
          args.map(transformTree(_)(owner).asInstanceOf[Term])
        )

      case _ =>
        super.transformTree(tree)(owner)
    }
  }

  /**
   * @param pending a map of type to `Expr[M[_]]` (as term)
   */
  private def createImplicit[M[_]](
      pending: Map[TypeRepr, Term],
      debug: String => Unit
  )(tc: Type[M], ptype: TypeRepr, tx: TreeMap): Option[Implicit] = {
    val pt              = ptype.asType
    val (ntpe, selfRef) = normalized(ptype)
    val ptpe            = ntpe

    // infers given
    val neededGivenType = TypeRepr.of[M](using tc).appliedTo(ptpe)

    val neededGiven: Option[Term] =
      pending
        .get(ptpe)
        .orElse(Implicits.search(neededGivenType) match {
          case suc: ImplicitSearchSuccess => {
            if (!selfRef) {
              Some(suc.tree)
            } else {
              tx.transformTree(suc.tree)(suc.tree.symbol) match {
                case t: Term => Some(t)
                case _       => Option.empty[Term]
              }
            }
          }

          case _ =>
            Option.empty[Term]
        })

    debug {
      val show: Option[String] =
        try {
          neededGiven.map(_.show)
        } catch {
          case e: MatchError /* Dotty bug */ =>
            neededGiven.map(_.symbol.fullName)
        }

      s"// Resolve given ${prettyType(TypeRepr.of(using tc))} for ${prettyType(ntpe)} as ${prettyType(
          neededGivenType
        )} (self? ${selfRef}) = ${show.mkString}"
    }

    neededGiven.map(_ -> selfRef)
  }

  /**
   * @param pending a map of type to `Expr[M[_]]` (as term)
   */
  protected[macros] def resolver[M[_], T](
      forwardExpr: Expr[M[T]],
      pending: Map[TypeRepr, Term],
      debug: String => Unit
  )(tc: Type[M]): TypeRepr => Option[Implicit] = {
    val tx = new ImplicitTransformer[M[T]](forwardExpr)

    createImplicit(pending, debug)(tc, _: TypeRepr, tx)
  }

  private def fullName(sym: Symbol): String =
    sym.fullName
      .replaceAll("(\\.package\\$|\\$|java\\.lang\\.|scala\\.Predef\\$\\.)", "")

  // To print the implicit types in the compiler messages
  protected final def prettyType(t: TypeRepr): String = t match {
    case _ if t <:< TypeRepr.of[EmptyTuple] =>
      "EmptyTuple"

    case AppliedType(ty, a :: b :: Nil) if ty <:< TypeRepr.of[*:] =>
      s"${prettyType(a)} *: ${prettyType(b)}"

    case AppliedType(_, args) =>
      fullName(t.typeSymbol) + args.map(prettyType).mkString("[", ", ", "]")

    case OrType(a, b) =>
      s"${prettyType(a)} | ${prettyType(b)}"

    case _ => {
      val sym = t.typeSymbol

      if (sym.isTypeParam) {
        sym.name
      } else {
        fullName(sym)
      }
    }
  }

  type Implicit = (Term, Boolean)
}

private[macros] object ImplicitResolver {
  def apply[T](q: Quotes)(using tpe: Type[T]): ImplicitResolver[T, q.type] =
    new ImplicitResolver[T, q.type] {
      import q.reflect.*

      val quotes   = q
      val aTpeRepr = TypeRepr.of[T](using tpe)
    }
}

/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.macros

import scala.reflect.macros.whitebox

import anorm.Macro.Placeholder

object ImplicitResolver {

  /**
   * @param boundTypes per each symbol of the type parameters,
   * which type is bound to
   */
  def apply(c: whitebox.Context)(
      tpe: c.Type,
      boundTypes: Map[String, c.Type],
      forwardName: c.TermName
  ): Function3[c.Name, c.Type, c.Type, Implicit[c.Type, c.Name, c.Tree]] = {
    import c.universe._

    // The placeholder type
    val PlaceholderType: Type = typeOf[Placeholder]

    new Function3[Name, Type, Type, Implicit[Type, Name, Tree]] {

      /**
       * @param name the name of the field
       * @param ptype the type of the field
       * @param typeclass the type of the higher kinded typeclass (e.g. `Column[_]`)
       */
      def apply(name: Name, ptype: Type, typeclass: Type): Implicit[Type, Name, Tree] = {
        val (ntpe, selfRef) = normalized(ptype)
        val ptpe            = boundTypes.getOrElse(ntpe.typeSymbol.fullName, ntpe)

        // infers implicit
        val neededImplicitType = appliedType(typeclass, ptpe)
        val neededImplicit = if (!selfRef) {
          c.inferImplicitValue(neededImplicitType)
        } else
          c.untypecheck(
            // Reset the type attributes on the refactored tree for the implicit
            ImplicitTransformer.transform(c.inferImplicitValue(neededImplicitType))
          )

        Implicit(name, ptype, neededImplicit, tpe, selfRef)
      }

      /* Refactor the input types, by replacing any type matching the `filter`,
       * by the given `replacement`.
       */
      @annotation.tailrec
      private def refactor(
          in: List[Type],
          base: TypeSymbol,
          out: List[Type],
          tail: List[(List[Type], TypeSymbol, List[Type])],
          filter: Type => Boolean,
          replacement: Type,
          altered: Boolean
      ): (Type, Boolean) = in match {
        case tpe :: ts =>
          boundTypes.getOrElse(tpe.typeSymbol.fullName, tpe) match {
            case t if filter(t) =>
              refactor(ts, base, replacement :: out, tail, filter, replacement, true)

            case TypeRef(_, sym, as) if as.nonEmpty =>
              refactor(as, sym.asType, List.empty, (ts, base, out) :: tail, filter, replacement, altered)

            case t => refactor(ts, base, t :: out, tail, filter, replacement, altered)
          }

        case _ => {
          val tpe = appliedType(base, out.reverse)

          tail match {
            case (x, y, more) :: ts =>
              refactor(x, y, tpe :: more, ts, filter, replacement, altered)

            case _ => tpe -> altered
          }
        }
      }

      /**
       * Replaces any reference to the type itself by the Placeholder type.
       * @return the normalized type + whether any self reference has been found
       */
      private def normalized(ptype: Type): (Type, Boolean) =
        boundTypes.getOrElse(ptype.typeSymbol.fullName, ptype) match {
          case t if t =:= tpe =>
            PlaceholderType -> true

          case TypeRef(_, sym, args) if args.nonEmpty =>
            refactor(args, sym.asType, List.empty, List.empty, _ =:= tpe, PlaceholderType, false)

          case t => t -> false
        }

      /* Restores reference to the type itself when Placeholder is found. */
      private def denormalized(ptype: Type): Type = ptype match {
        case PlaceholderType => tpe

        case TypeRef(_, sym, args) =>
          refactor(args, sym.asType, List.empty, List.empty, _ == PlaceholderType, tpe, false)._1

        case _ => ptype
      }

      private object ImplicitTransformer extends Transformer {
        override def transform(tree: Tree): Tree = tree match {
          case tt: TypeTree =>
            super.transform(TypeTree(denormalized(tt.tpe)))

          case Select(Select(This(TypeName("Macro")), t), sym)
              if t.toString == "Placeholder" && sym.toString == "Parser" =>
            super.transform(q"$forwardName")

          case _ =>
            super.transform(tree)
        }
      }
    }
  }
}

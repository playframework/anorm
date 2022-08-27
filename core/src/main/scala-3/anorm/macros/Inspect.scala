package anorm.macros

import scala.quoted.{ Expr, Quotes, Type }

import anorm.Compat

private[anorm] object Inspect:

  /**
   * Recursively find the sub-classes of `tpr`.
   *
   * Non case class and generic classes are ignored.
   *
   * Sub-abstract types are not listed, but their own sub-types are examined;
   * e.g. for trait `Foo`
   *
   * {{{
   * sealed trait Foo
   * case class Bar(name: String) extends Foo
   * sealed trait SubFoo extends Foo
   * case class Lorem() extends SubFoo
   * }}}
   *
   * Class `Lorem` is listed through `SubFoo`,
   * but `SubFoo` itself is not returned.
   */
  final def knownSubclasses(
      q: Quotes
  )(tpr: q.reflect.TypeRepr)(using Type[AnyVal]): Option[List[q.reflect.TypeRepr]] = {
    import q.reflect.*

    tpr.classSymbol.flatMap { cls =>
      val anyValTpe: TypeRepr = TypeRepr.of[AnyVal]

      @annotation.tailrec
      def subclasses(
          children: List[Tree],
          out: List[TypeRepr]
      ): List[TypeRepr] = {
        val childTpr = children.headOption.collect {
          case tpd: Typed =>
            tpd.tpt.tpe

          case vd: ValDef =>
            vd.tpt.tpe

          case cd: ClassDef =>
            cd.constructor.returnTpt.tpe

        }

        childTpr match {
          case Some(generic @ AppliedType(_, args)) if args.nonEmpty => {
            report.warning(s"cannot handle class ${generic.show}: type parameter not supported")

            subclasses(children.tail, out)
          }

          case Some(child) => {
            val tpeSym = child.typeSymbol
            val flags  = tpeSym.flags

            if (
              (flags.is(Flags.Abstract) && flags.is(Flags.Sealed) &&
                !(child <:< anyValTpe)) ||
              (flags.is(Flags.Sealed) && flags.is(Flags.Trait))
            ) {
              // Ignore sub-trait itself, but check the sub-sub-classes
              subclasses(tpeSym.children.map(_.tree) ::: children.tail, out)
            } else if (!flags.is(Flags.Case)) {
              report.warning(s"cannot handle class ${child.show}: no case accessor")

              subclasses(children.tail, out)
            } else {
              subclasses(children.tail, child :: out)
            }
          }

          case _ =>
            out.reverse
        }
      }

      val types = subclasses(cls.children.map(_.tree), Nil)

      if (types.isEmpty) None else Some(types)
    }
  }

end Inspect

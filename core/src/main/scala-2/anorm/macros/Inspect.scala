package anorm.macros

import scala.reflect.macros.whitebox

import anorm.Compat

private[anorm] object Inspect {
  def directKnownSubclasses(c: whitebox.Context)(tpe: c.Type): List[c.Type] = {
    // Workaround for SI-7046: https://issues.scala-lang.org/browse/SI-7046
    import c.universe._

    val tpeSym = tpe.typeSymbol.asClass

    @annotation.tailrec
    def allSubclasses(path: Compat.Trav[Symbol], subclasses: Set[Type]): Set[Type] = path.headOption match {
      case Some(cls: ClassSymbol) if tpeSym != cls && cls.selfType.baseClasses.contains(tpeSym) => {
        val newSub: Set[Type] = if (!cls.isCaseClass) {
          c.warning(c.enclosingPosition, s"cannot handle class ${cls.fullName}: no case accessor")
          Set.empty
        } else if (cls.typeParams.nonEmpty) {
          c.warning(c.enclosingPosition, s"cannot handle class ${cls.fullName}: type parameter not supported")
          Set.empty
        } else Set(cls.selfType)

        allSubclasses(path.tail, subclasses ++ newSub)
      }

      case Some(o: ModuleSymbol)
          if o.companion == NoSymbol && // not a companion object
            o.typeSignature.baseClasses.contains(tpeSym) => {
        val newSub: Set[Type] = if (!o.moduleClass.asClass.isCaseClass) {
          c.warning(c.enclosingPosition, s"cannot handle object ${o.fullName}: no case accessor")
          Set.empty
        } else Set(o.typeSignature)

        allSubclasses(path.tail, subclasses ++ newSub)
      }

      case Some(o: ModuleSymbol)
          if o.companion == NoSymbol // not a companion object
          =>
        allSubclasses(path.tail, subclasses)

      case Some(_) => allSubclasses(path.tail, subclasses)

      case _ => subclasses
    }

    if (tpeSym.isSealed && tpeSym.isAbstract) {
      allSubclasses(tpeSym.owner.typeSignature.decls, Set.empty).toList
    } else List.empty
  }

  def boundTypes(c: whitebox.Context)(tpe: c.Type): Map[String, c.Type] = {
    import c.universe._

    val tpeArgs: List[c.Type] = tpe match {
      case TypeRef(_, _, args)        => args
      case i @ ClassInfoType(_, _, _) => i.typeArgs
      case _                          => List.empty
    }

    val companion = tpe.typeSymbol.companion.typeSignature
    val apply     = companion.decl(TermName("apply")).asMethod

    if (tpeArgs.isEmpty) Map.empty
    else {
      // Need apply rather than ctor to resolve parameter symbols

      if (apply.paramLists.isEmpty) {
        Map.empty
      } else {
        Compat.toMap(Compat.lazyZip(apply.typeParams, tpeArgs)) {
          case (sym, ty) =>
            sym.fullName -> ty
        }
      }
    }
  }

  def pretty(c: whitebox.Context)(tree: c.Tree): String =
    c.universe
      .show(tree)
      .replaceAll("anorm.", "")
      .replaceAll(f"\\.\\$$tilde", " ~ ")
      .replaceAll("\\(SqlParser([^(]+)\\(([^)]+)\\)\\)", f"SqlParser$$1($$2)")
      .replaceAll(f"\\.\\$$plus\\(([0-9]+)\\)", f" + $$1")
      .replaceAll("\\(([^ ]+) @ _\\)", f"($$1)")
      .replaceAll(f"\\$$tilde", "~")

}

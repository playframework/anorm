package anorm

/**
 * @define caseTParam the type of case class
 * @define namingParam the column naming, to resolve the column name for each case class property
 * @define namesParam the names of the columns corresponding to the case class properties
 * @define sealedParserDoc Returns a row parser generated
 * for a sealed class family.
 * Each direct known subclasses `C` must be provided with an appropriate
 * `RowParser[C]` in the implicit scope.
 *
 * @define discriminatorNamingParam the naming function for the discriminator column
 * @define discriminateParam the discriminating function applied to each name of the family type
 * @define familyTParam the type of the type family (either a sealed trait or abstract class)
 */
object Macro {
  import scala.language.experimental.macros
  import scala.reflect.macros.whitebox

  /** Only for internal purposes */
  final class Placeholder {}

  /** Only for internal purposes */
  object Placeholder {
    implicit object Parser extends RowParser[Placeholder] {
      val success = Success(new Placeholder())

      def apply(row: Row) = success
    }
  }

  /**
   * Naming strategy, to map each class property to the corresponding column.
   */
  trait ColumnNaming extends (String => String) {
    /**
     * Returns the column name for the class property.
     *
     * @param property the name of the case class property
     */
    def apply(property: String): String
  }

  /** Naming companion */
  object ColumnNaming {
    /**
     * For each class property, use the snake case equivalent
     * to name its column (e.g. fooBar -> foo_bar).
     */
    object SnakeCase extends ColumnNaming {
      private val re = "[A-Z]+".r

      def apply(property: String): String =
        re.replaceAllIn(property, { m => s"_${m.matched.toLowerCase}" })
    }

    /** Naming using a custom transformation function. */
    def apply(transformation: String => String): ColumnNaming =
      new ColumnNaming {
        def apply(property: String): String = transformation(property)
      }
  }

  trait Discriminate extends (String => String) {
    /**
     * Returns the value representing the specified type,
     * to be used as a discriminator within a sealed family.
     *
     * @param tname the name of type (class or object) to be discriminated
     */
    def apply(tname: String): String
  }

  object Discriminate {
    sealed class Function(f: String => String) extends Discriminate {
      def apply(tname: String) = f(tname)
    }

    /** Uses the type name as-is as value for the discriminator */
    object Identity extends Function(identity[String])

    /** Returns a `Discriminate` function from any `String => String`. */
    def apply(discriminate: String => String): Discriminate =
      new Function(discriminate)
  }

  trait DiscriminatorNaming extends (String => String) {
    /**
     * Returns the name for the discriminator column.
     * @param familyType the name of the famility type (sealed trait)
     */
    def apply(familyType: String): String
  }

  object DiscriminatorNaming {
    sealed class Function(f: String => String) extends DiscriminatorNaming {
      def apply(familyType: String) = f(familyType)
    }

    /** Always use "classname" as name for the discriminator column. */
    object Default extends Function(_ => "classname")

    /** Returns a naming according from any `String => String`. */
    def apply(naming: String => String): DiscriminatorNaming =
      new Function(naming)
  }

  // ---

  def namedParserImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[T] = {
    import c.universe._

    parserImpl[T](c) { (t, n, _) => q"anorm.SqlParser.get[$t]($n)" }
  }

  def namedParserImpl1[T: c.WeakTypeTag](c: whitebox.Context)(naming: c.Expr[ColumnNaming]): c.Expr[T] = {
    import c.universe._

    parserImpl[T](c) { (t, n, _) => q"anorm.SqlParser.get[$t]($naming($n))" }
  }

  @deprecated("Use [[namedParserImpl2]]", "2.5.2")
  def namedParserImpl_[T: c.WeakTypeTag](c: whitebox.Context)(names: c.Expr[String]*): c.Expr[T] = namedParserImpl2[T](c)(names: _*)

  def namedParserImpl2[T: c.WeakTypeTag](c: whitebox.Context)(names: c.Expr[String]*): c.Expr[T] = {
    import c.universe._

    namedParserImpl4[T](c)(names) { n => q"$n" }
  }

  def namedParserImpl3[T: c.WeakTypeTag](c: whitebox.Context)(naming: c.Expr[ColumnNaming], names: c.Expr[String]*): c.Expr[T] = {
    import c.universe._

    namedParserImpl4[T](c)(names) { n => q"$naming($n)" }
  }

  private def namedParserImpl4[T: c.WeakTypeTag](c: whitebox.Context)(names: Seq[c.Expr[String]])(naming: c.Expr[String] => c.universe.Tree): c.Expr[T] = {
    import c.universe._

    val tpe = c.weakTypeTag[T].tpe
    val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod
    val params = ctor.paramLists.flatten

    if (names.size < params.size) {
      c.abort(c.enclosingPosition,
        s"no column name for parameters: ${show(names)} < $params")

    } else {
      parserImpl[T](c) { (t, _, i) =>
        names.lift(i) match {
          case Some(n) => {
            val cn = naming(n)
            q"anorm.SqlParser.get[$t]($cn)"
          }

          case _ => c.abort(c.enclosingPosition,
            s"missing column name for parameter $i")
        }
      }
    }
  }

  def offsetParserImpl[T: c.WeakTypeTag](c: whitebox.Context)(offset: c.Expr[Int]): c.Expr[T] = {
    import c.universe._

    parserImpl[T](c) { (t, _, i) =>
      q"anorm.SqlParser.get[$t]($offset + ${i + 1})"
    }
  }

  def indexedParserImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[T] = {
    import c.universe._

    offsetParserImpl[T](c)(reify(0))
  }

  private def directKnownSubclasses(c: whitebox.Context)(tpe: c.Type): List[c.Type] = {
    // Workaround for SI-7046: https://issues.scala-lang.org/browse/SI-7046
    import c.universe._

    val tpeSym = tpe.typeSymbol.asClass

    @annotation.tailrec
    def allSubclasses(path: Traversable[Symbol], subclasses: Set[Type]): Set[Type] = path.headOption match {
      case Some(cls: ClassSymbol) if (
        tpeSym != cls && cls.selfType.baseClasses.contains(tpeSym)
      ) => {
        val newSub: Set[Type] = if (!cls.isCaseClass) {
          c.warning(c.enclosingPosition, s"cannot handle class ${cls.fullName}: no case accessor")
          Set.empty
        } else if (!cls.typeParams.isEmpty) {
          c.warning(c.enclosingPosition, s"cannot handle class ${cls.fullName}: type parameter not supported")
          Set.empty
        } else Set(cls.selfType)

        allSubclasses(path.tail, subclasses ++ newSub)
      }

      case Some(o: ModuleSymbol) if (
        o.companion == NoSymbol && // not a companion object
        tpeSym != c && o.typeSignature.baseClasses.contains(tpeSym)
      ) => {
        val newSub: Set[Type] = if (!o.moduleClass.asClass.isCaseClass) {
          c.warning(c.enclosingPosition, s"cannot handle object ${o.fullName}: no case accessor")
          Set.empty
        } else Set(o.typeSignature)

        allSubclasses(path.tail, subclasses ++ newSub)
      }

      case Some(o: ModuleSymbol) if (
        o.companion == NoSymbol // not a companion object
      ) => allSubclasses(path.tail, subclasses)

      case Some(_) => allSubclasses(path.tail, subclasses)

      case _ => subclasses
    }

    if (tpeSym.isSealed && tpeSym.isAbstract) {
      allSubclasses(tpeSym.owner.typeSignature.decls, Set.empty).toList
    } else List.empty
  }

  def sealedParserImpl1[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[RowParser[T]] = {
    import c.universe.reify
    sealedParserImpl(c)(
      reify(DiscriminatorNaming.Default), reify(Discriminate.Identity))
  }

  def sealedParserImpl2[T: c.WeakTypeTag](c: whitebox.Context)(naming: c.Expr[DiscriminatorNaming]): c.Expr[RowParser[T]] = sealedParserImpl(c)(naming, c.universe.reify(Discriminate.Identity))

  def sealedParserImpl3[T: c.WeakTypeTag](c: whitebox.Context)(discriminate: c.Expr[Discriminate]): c.Expr[RowParser[T]] = sealedParserImpl(c)(c.universe.reify(DiscriminatorNaming.Default), discriminate)

  def sealedParserImpl[T: c.WeakTypeTag](c: whitebox.Context)(naming: c.Expr[DiscriminatorNaming], discriminate: c.Expr[Discriminate]): c.Expr[RowParser[T]] = {
    import c.universe._

    val tpe = c.weakTypeTag[T].tpe

    @inline def abort(msg: String) = c.abort(c.enclosingPosition, msg)
    val sub = directKnownSubclasses(c)(tpe).filter { subclass =>
      if (!subclass.typeSymbol.asClass.typeParams.isEmpty) {
        c.warning(c.enclosingPosition, s"class with type parameters is not supported as family member: $subclass")

        false
      } else true
    }

    if (sub.isEmpty) {
      abort(s"cannot find any subclass: $tpe")
    }

    val parserTpe = c.weakTypeTag[RowParser[_]].tpe
    val missing: List[Type] = sub.flatMap { subclass =>
      val ptype = appliedType(parserTpe, List(subclass))

      c.inferImplicitValue(ptype) match {
        case EmptyTree => List(subclass)
        case _ => List.empty
      }
    }

    if (!missing.isEmpty) {
      def details = missing.map { subclass =>
        val typeStr = if (subclass.typeSymbol.companion == NoSymbol) {
          s"${subclass.typeSymbol.fullName}.type"
        } else subclass.typeSymbol.fullName

        s"- cannot find anorm.RowParser[$typeStr] in the implicit scope"
      }.mkString(",\r\n")

      abort(s"fails to generate sealed parser: $tpe;\r\n$details")
    }

    // ---

    val cases = sub.map { subclass =>
      val caseName = TermName(c.freshName("discriminated"))
      val key = q"$discriminate(${subclass.typeSymbol.fullName})"
      val caseDecl = q"val $caseName = $key"
      val subtype = {
        if (subclass.typeSymbol.asClass.typeParams.isEmpty) subclass
        else subclass.erasure
      }

      (key, caseDecl, cq"`$caseName` => implicitly[anorm.RowParser[$subtype]]")
    }

    lazy val supported = q"List(..${cases.map(_._1)})"
    def mappingError = q"""anorm.RowParser.failed[$tpe](anorm.Error(anorm.SqlMappingError("unexpected row type '%s'; expected: %s".format(d, $supported))))"""

    val discriminatorTerm = TermName(c.freshName("discriminator"))
    val colTerm = TermName(c.freshName("column"))
    val matching = Match(
      q"$discriminatorTerm", cases.map(_._3) :+ cq"d => $mappingError")

    val parser = q"""new anorm.RowParser[$tpe] {
      val $colTerm = $naming(${tpe.typeSymbol.fullName})
      val underlying: anorm.RowParser[$tpe] = 
        anorm.SqlParser.str($colTerm).flatMap { $discriminatorTerm: String => 
          ..${cases.map(_._2) :+ matching}
        }

      def apply(row: Row): anorm.SqlResult[$tpe] = underlying(row)
    }"""

    if (debugEnabled) {
      c.echo(c.enclosingPosition, s"row parser generated for $tpe: ${pretty(c)(parser)}")
    }

    c.Expr[RowParser[T]](c.typecheck(parser))
  }

  private def parserImpl[T: c.WeakTypeTag](c: whitebox.Context)(genGet: (c.universe.Type, String, Int) => c.universe.Tree): c.Expr[T] = {
    import c.universe._

    val tpe = c.weakTypeTag[T].tpe
    @inline def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    if (!tpe.typeSymbol.isClass || !tpe.typeSymbol.asClass.isCaseClass) {
      abort(s"case class expected: $tpe")
    }

    val colTpe = c.weakTypeTag[Column[_]].tpe
    val parserTpe = c.weakTypeTag[RowParser[_]].tpe
    val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod

    if (ctor.paramLists.isEmpty) {
      abort(s"parsed data cannot be passed as parameter: $ctor")
    }

    val tpeArgs: List[c.Type] = tpe match {
      case SingleType(_, _) => List.empty
      case TypeRef(_, _, args) => args
      case i @ ClassInfoType(_, _, _) => i.typeArgs
    }

    val companion = tpe.typeSymbol.companion.typeSignature
    val apply = companion.decl(TermName("apply")).asMethod

    object ImplicitResolver {
      // Per each symbol of the type parameters, which type is bound to
      val boundTypes: Map[String, Type] = if (tpeArgs.isEmpty) Map.empty else {
        // Need apply rather than ctor to resolve parameter symbols

        if (apply.paramLists.isEmpty) Map.empty
        else apply.typeParams.zip(tpeArgs).map {
          case (sym, ty) => sym.fullName -> ty
        }.toMap
      }

      // The placeholder type
      private val PlaceholderType: Type = typeOf[Placeholder]

      /* Refactor the input types, by replacing any type matching the `filter`,
       * by the given `replacement`.
       */
      @annotation.tailrec
      private def refactor(in: List[Type], base: TypeSymbol, out: List[Type], tail: List[(List[Type], TypeSymbol, List[Type])], filter: Type => Boolean, replacement: Type, altered: Boolean): (Type, Boolean) = in match {
        case tpe :: ts =>
          boundTypes.getOrElse(tpe.typeSymbol.fullName, tpe) match {
            case t if (filter(t)) =>
              refactor(ts, base, (replacement :: out), tail,
                filter, replacement, true)

            case TypeRef(_, sym, as) if as.nonEmpty =>
              refactor(as, sym.asType, List.empty, (ts, base, out) :: tail,
                filter, replacement, altered)

            case t => refactor(ts, base, (t :: out), tail,
              filter, replacement, altered)
          }

        case _ => {
          val tpe = appliedType(base, out.reverse)

          tail match {
            case (x, y, more) :: ts =>
              refactor(x, y, (tpe :: more), ts, filter, replacement, altered)

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
          case t if (t =:= tpe) =>
            PlaceholderType -> true

          case TypeRef(_, sym, args) if args.nonEmpty =>
            refactor(args, sym.asType, List.empty, List.empty,
              _ =:= tpe, PlaceholderType, false)

          case t => t -> false
        }

      /* Restores reference to the type itself when Placeholder is found. */
      private def denormalized(ptype: Type): Type = ptype match {
        case PlaceholderType => tpe

        case TypeRef(_, sym, args) =>
          refactor(args, sym.asType, List.empty, List.empty,
            _ == PlaceholderType, tpe, false)._1

        case _ => ptype
      }

      val forwardName = TermName(c.freshName("forward"))

      private object ImplicitTransformer extends Transformer {
        override def transform(tree: Tree): Tree = tree match {
          case tt: TypeTree =>
            super.transform(TypeTree(denormalized(tt.tpe)))

          case Select(Select(This(TypeName("Macro")), t), sym) if (
            t.toString == "Placeholder" && sym.toString == "Parser"
          ) => super.transform(q"$forwardName")

          case _ =>
            super.transform(tree)
        }
      }

      def resolve(name: Name, ptype: Type, typeclass: Type): Implicit = {
        val (ntpe, selfRef) = normalized(ptype)
        val ptpe = boundTypes.get(ntpe.typeSymbol.fullName).getOrElse(ntpe)

        // infers implicit
        val neededImplicitType = appliedType(typeclass, ptpe)
        val neededImplicit = if (!selfRef) {
          c.inferImplicitValue(neededImplicitType)
        } else c.untypecheck(
          // Reset the type attributes on the refactored tree for the implicit
          ImplicitTransformer.transform(
            c.inferImplicitValue(neededImplicitType)))

        Implicit(name, ptype, neededImplicit, tpe, selfRef)
      }

      // ---

      // Now we find all the implicits that we need
      final case class Implicit(
        paramName: Name,
        paramType: Type,
        neededImplicit: Tree,
        tpe: Type,
        selfRef: Boolean)
    }

    // ---

    import ImplicitResolver.Implicit

    val (x, m, body, _, hasSelfRef) = ctor.paramLists.foldLeft[(Tree, Tree, Tree, Int, Boolean)]((EmptyTree, EmptyTree, EmptyTree, 0, false)) {
      case ((xa, ma, bs, ia, sr), pss) =>
        val (xb, mb, vs, ib, selfRef) =
          pss.foldLeft((xa, ma, List.empty[Tree], ia, sr)) {
            case ((xtr, mp, ps, pi, sref), term: TermSymbol) =>
              val tn = term.name.toString
              val tt = {
                val t = term.typeSignature
                ImplicitResolver.boundTypes.
                  getOrElse(t.typeSymbol.fullName, t)
                // TODO: term.isParamWithDefault
              }

              // Try to resolve `Column[tt]`
              ImplicitResolver.resolve(term.name, tt, colTpe) match {
                case Implicit(_, _, EmptyTree, _, _) => // No `Column[tt]` ...
                  // ... try to resolve `RowParser[tt]`
                  ImplicitResolver.resolve(term.name, tt, parserTpe) match {
                    case Implicit(_, _, EmptyTree, _, _) => abort(s"cannot find $colTpe nor $parserTpe for ${term.name}:$tt in $ctor")

                    case Implicit(_, _, pr, _, s) => {
                      // Use an existing `RowParser[T]` as part
                      pq"${term.name}" match {
                        case b @ Bind(bn, _) =>
                          val bt = q"${bn.toTermName}"

                          xtr match {
                            case EmptyTree =>
                              (pr, b, List[Tree](bt), pi + 1, s || sref)

                            case _ => (q"$xtr ~ $pr",
                              pq"anorm.~($mp, $b)", bt :: ps, pi + 1, s || sref)

                          }
                      }
                    }
                  }

                case Implicit(_, _, itree, _, _) => {
                  // Generate a `get` for the `Column[T]`
                  val get = genGet(tt, tn, pi)

                  pq"${term.name}" match {
                    case b @ Bind(bn, _) =>
                      val bt = q"${bn.toTermName}"

                      xtr match {
                        case EmptyTree =>
                          (get, b, List[Tree](bt), pi + 1, sref)

                        case _ => (q"$xtr ~ $get($itree)",
                          pq"anorm.~($mp, $b)", bt :: ps, pi + 1, sref)

                      }
                  }
                }
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
          val ${ImplicitResolver.forwardName} = 
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

  private def pretty(c: whitebox.Context)(parser: c.Tree): String =
    c.universe.show(parser).replaceAll("anorm.", "").
      replaceAll("\\.\\$tilde", " ~ ").
      replaceAll("\\(SqlParser([^(]+)\\(([^)]+)\\)\\)", "SqlParser$1($2)").
      replaceAll("\\.\\$plus\\(([0-9]+)\\)", " + $1").
      replaceAll("\\(([^ ]+) @ _\\)", "($1)").
      replaceAll("\\$tilde", "~")

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by name.
   *
   * @tparam T $caseTParam
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] = Macros.namedParser[YourCaseClass]
   * }}}
   */
  def namedParser[T]: RowParser[T] = macro namedParserImpl[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by name.
   *
   * @tparam T $caseTParam
   * @param naming $namingParam
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] = Macros.namedParser[YourCaseClass]
   * }}}
   */
  def namedParser[T](naming: Macro.ColumnNaming): RowParser[T] = macro namedParserImpl1[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values according the property `names`.
   *
   * @tparam T $caseTParam
   * @param names $namesParam
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] =
   *   Macros.parser[YourCaseClass]("foo", "bar")
   * }}}
   */
  def parser[T](names: String*): RowParser[T] = macro namedParserImpl2[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values according the property `names`.
   *
   * @tparam T $caseTParam
   *
   * @param naming $namingParam
   * @param names $namesParam
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] =
   *   Macros.parser[YourCaseClass]("foo", "loremIpsum")
   * }}}
   */
  def parser[T](naming: Macro.ColumnNaming, names: String*): RowParser[T] = macro namedParserImpl3[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by position.
   *
   * @tparam T $caseTParam
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] = Macros.indexedParser[YourCaseClass]
   * }}}
   */
  def indexedParser[T]: RowParser[T] = macro indexedParserImpl[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by position, with an offset.
   *
   * @tparam T $caseTParam
   * @param offset the offset of column to be considered by the parser
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] = Macros.offsetParser[YourCaseClass](2)
   * }}}
   */
  def offsetParser[T](offset: Int): RowParser[T] = macro offsetParserImpl[T]

  /**
   * $sealedParserDoc
   * The default naming is used.
   *
   * @tparam T $familyTParam
   */
  def sealedParser[T]: RowParser[T] = macro sealedParserImpl1[T]

  /**
   * $sealedParserDoc
   *
   * @param naming $discriminatorNamingParam
   * @tparam T $familyTParam
   */
  def sealedParser[T](naming: Macro.DiscriminatorNaming): RowParser[T] = macro sealedParserImpl2[T]

  /**
   * $sealedParserDoc
   *
   * @param discriminate $discriminateParam
   * @tparam T $familyTParam
   */
  def sealedParser[T](discriminate: Macro.Discriminate): RowParser[T] = macro sealedParserImpl3[T]

  /**
   * $sealedParserDoc
   *
   * @param naming $discriminatorNamingParam
   * @param discriminate $discriminateParam
   * @tparam T $familyTParam
   */
  def sealedParser[T](naming: Macro.DiscriminatorNaming, discriminate: Macro.Discriminate): RowParser[T] = macro sealedParserImpl[T]

  private lazy val debugEnabled =
    Option(System.getProperty("anorm.macro.debug")).
      filterNot(_.isEmpty).map(_.toLowerCase).map { v =>
        "true".equals(v) || v.substring(0, 1) == "y"
      }.getOrElse(false)

}

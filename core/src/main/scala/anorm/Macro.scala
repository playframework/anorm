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
 * @define separatorParam the separator used with nested properties
 * @define projectionParam The optional projection for the properties as parameters; If none, using the all the class properties.
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
    /** Keep the original property name. */
    object Identity extends ColumnNaming {
      def apply(property: String) = property
    }

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
      c.abort(
        c.enclosingPosition,
        s"no column name for parameters: ${show(names)} < $params")

    } else {
      parserImpl[T](c) { (t, _, i) =>
        names.lift(i) match {
          case Some(n) => {
            val cn = naming(n)
            q"anorm.SqlParser.get[$t]($cn)"
          }

          case _ => c.abort(
            c.enclosingPosition,
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

  def sealedParserImpl1[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[RowParser[T]] = {
    import c.universe.reify
    sealedParserImpl(c)(
      reify(DiscriminatorNaming.Default), reify(Discriminate.Identity))
  }

  def sealedParserImpl2[T: c.WeakTypeTag](c: whitebox.Context)(naming: c.Expr[DiscriminatorNaming]): c.Expr[RowParser[T]] = sealedParserImpl(c)(naming, c.universe.reify(Discriminate.Identity))

  def sealedParserImpl3[T: c.WeakTypeTag](c: whitebox.Context)(discriminate: c.Expr[Discriminate]): c.Expr[RowParser[T]] = sealedParserImpl(c)(c.universe.reify(DiscriminatorNaming.Default), discriminate)

  def sealedParserImpl[T: c.WeakTypeTag](c: whitebox.Context)(naming: c.Expr[DiscriminatorNaming], discriminate: c.Expr[Discriminate]): c.Expr[RowParser[T]] = anorm.macros.SealedRowParserImpl[T](c)(naming, discriminate)

  private def parserImpl[T: c.WeakTypeTag](c: whitebox.Context)(genGet: (c.universe.Type, String, Int) => c.universe.Tree): c.Expr[T] = anorm.macros.RowParserImpl[T](c)(genGet)

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
  @SuppressWarnings(Array("UnusedMethodParameter" /* macro */ ))
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
  @SuppressWarnings(Array("UnusedMethodParameter" /* macro */ ))
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
  @SuppressWarnings(Array("UnusedMethodParameter" /* macro */ ))
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
  @SuppressWarnings(Array("UnusedMethodParameter" /* macro */ ))
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
  @SuppressWarnings(Array("UnusedMethodParameter" /* macro */ ))
  def sealedParser[T](naming: Macro.DiscriminatorNaming): RowParser[T] = macro sealedParserImpl2[T]

  /**
   * $sealedParserDoc
   *
   * @param discriminate $discriminateParam
   * @tparam T $familyTParam
   */
  @SuppressWarnings(Array("UnusedMethodParameter" /* macro */ ))
  def sealedParser[T](discriminate: Macro.Discriminate): RowParser[T] = macro sealedParserImpl3[T]

  /**
   * $sealedParserDoc
   *
   * @param naming $discriminatorNamingParam
   * @param discriminate $discriminateParam
   * @tparam T $familyTParam
   */
  @SuppressWarnings(Array("UnusedMethodParameter" /* macro */ ))
  def sealedParser[T](naming: Macro.DiscriminatorNaming, discriminate: Macro.Discriminate): RowParser[T] = macro sealedParserImpl[T]

  import anorm.macros.ToParameterListImpl

  /**
   * @param separator $separatorParam
   * @tparam T $caseTParam
   *
   * {{{
   * import anorm.{ Macro, ToParameterList }
   *
   * // Bar must be a case class, or a sealed trait with known subclasses
   * implicit val toParams: ToParameterList[Bar] = Macro.toParameters[Bar]
   * }}}
   */
  def toParameters[T]: ToParameterList[T] = macro defaultParameters[T]

  def defaultParameters[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[ToParameterList[T]] = {
    val tpe = c.weakTypeTag[T].tpe
    val tpeSym = tpe.typeSymbol.asClass

    @inline def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    if (tpeSym.isSealed && tpeSym.isAbstract) {
      ToParameterListImpl.sealedTrait[T](c)
    } else if (!tpeSym.isClass || !tpeSym.asClass.isCaseClass) {
      abort(s"Either a sealed trait or a case class expected: $tpe")
    } else {
      ToParameterListImpl.caseClass[T](c)(
        Seq.empty[c.Expr[Macro.ParameterProjection]], c.universe.reify("_"))
    }
  }

  /**
   * @param separator $separatorParam
   * @tparam T $caseTParam
   *
   * {{{
   * import anorm.{ Macro, ToParameterList }
   *
   * // Bar must be a case class
   * implicit val toParams: ToParameterList[Bar] =
   *   Macro.toParameters[Bar](separator = "_")
   * }}}
   */
  def toParameters[T](separator: String): ToParameterList[T] = macro parametersDefaultNames[T]

  def parametersDefaultNames[T: c.WeakTypeTag](c: whitebox.Context)(separator: c.Expr[String]): c.Expr[ToParameterList[T]] = ToParameterListImpl.caseClass[T](c)(Seq.empty[c.Expr[Macro.ParameterProjection]], separator)

  /**
   * @param projection $projectionParam
   * @tparam T $caseTParam
   *
   * {{{
   * import anorm.{ Macro, ToParameterList }
   *
   * // Bar must be a case class
   * implicit val toParams: ToParameterList[Bar] =
   *   Macro.toParameters[Bar]()
   * }}}
   */
  def toParameters[T](projection: Macro.ParameterProjection*): ToParameterList[T] = macro configuredParameters[T]

  def configuredParameters[T: c.WeakTypeTag](c: whitebox.Context)(projection: c.Expr[Macro.ParameterProjection]*): c.Expr[ToParameterList[T]] = {
    import c.universe.reify

    ToParameterListImpl.caseClass[T](c)(projection, reify("_"))
  }

  /**
   * @param separator $separatorParam
   * @param projection $projectionParam
   * @tparam T $caseTParam
   */
  def toParameters[T](separator: String, projection: Macro.ParameterProjection*): ToParameterList[T] = macro parametersWithSeparator[T]

  def parametersWithSeparator[T: c.WeakTypeTag](c: whitebox.Context)(separator: c.Expr[String], projection: c.Expr[Macro.ParameterProjection]*): c.Expr[ToParameterList[T]] = ToParameterListImpl.caseClass[T](c)(projection, separator)

  /**
   * @param propertyName the name of the class property
   * @param parameterName the name of for the parameter,
   * if different from the property one, otherwise `None`
   */
  case class ParameterProjection(
    propertyName: String,
    parameterName: Option[String] = None)

  object ParameterProjection {
    def apply(
      propertyName: String,
      parameterName: String): ParameterProjection =
      ParameterProjection(propertyName, Option(parameterName))
  }

  private[anorm] lazy val debugEnabled =
    Option(System.getProperty("anorm.macro.debug")).
      filterNot(_.isEmpty).map(_.toLowerCase).map { v =>
        "true".equals(v) || v.substring(0, 1) == "y"
      }.getOrElse(false)

}

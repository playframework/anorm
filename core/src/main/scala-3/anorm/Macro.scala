package anorm

import scala.quoted.{ Expr, Quotes, Type }

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
 * @define valueClassTParam the type of the value class
 */
object Macro extends MacroOptions:
  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by name.
   *
   * @tparam T $caseTParam
   *
   * {{{
   * import anorm.{ Macro, RowParser }
   *
   * case class YourCaseClass(v: Int)
   *
   * val p: RowParser[YourCaseClass] = Macro.namedParser[YourCaseClass]
   * }}}
   */
  inline def namedParser[T]: RowParser[T] = ${ namedParserImpl[T] }

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by name.
   *
   * @tparam T $caseTParam
   * @param naming $namingParam
   *
   * {{{
   * import anorm.{ Macro, RowParser }
   *
   * case class YourCaseClass(v: Int)
   *
   * val p: RowParser[YourCaseClass] = Macro.namedParser[YourCaseClass]
   * }}}
   */
  inline def namedParser[T](naming: Macro.ColumnNaming): RowParser[T] =
    ${ namedParserImpl1[T]('naming) }

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values according the property `names`.
   *
   * @tparam T $caseTParam
   * @param names $namesParam
   *
   * {{{
   * import anorm.{ Macro, RowParser }
   *
   * case class YourCaseClass(a: Int, b: String)
   *
   * val p: RowParser[YourCaseClass] =
   *   Macro.parser[YourCaseClass]("foo", "bar")
   * }}}
   */
  inline def parser[T](names: String*): RowParser[T] =
    ${ namedParserImpl2[T]('names) }

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
   * import anorm.{ Macro, RowParser }
   *
   * case class YourCaseClass(a: Int, b: String)
   *
   * val p: RowParser[YourCaseClass] =
   *   Macro.parser[YourCaseClass]("foo", "loremIpsum")
   * }}}
   */
  inline def parser[T](naming: Macro.ColumnNaming, names: String*): RowParser[T] = ${
    namedParserImpl3[T]('naming, 'names)
  }

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by position.
   *
   * @tparam T $caseTParam
   *
   * {{{
   * import anorm.{ Macro, RowParser }
   *
   * case class YourCaseClass(v: Int)
   *
   * val p: RowParser[YourCaseClass] = Macro.indexedParser[YourCaseClass]
   * }}}
   */
  inline def indexedParser[T]: RowParser[T] = ${ indexedParserImpl[T] }

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by position, with an offset.
   *
   * @tparam T $caseTParam
   * @param offset the offset of column to be considered by the parser
   *
   * {{{
   * import anorm.{ Macro, RowParser }
   *
   * case class YourCaseClass(v: Int)
   *
   * val p: RowParser[YourCaseClass] = Macro.offsetParser[YourCaseClass](2)
   * }}}
   */
  inline def offsetParser[T](offset: Int): RowParser[T] =
    ${ offsetParserImpl[T]('offset) }

  /**
   * $sealedParserDoc
   * The default naming is used.
   *
   * @tparam T $familyTParam
   */
  inline def sealedParser[T]: RowParser[T] = ${ sealedParserImpl1[T] }

  /**
   * $sealedParserDoc
   *
   * @param naming $discriminatorNamingParam
   * @tparam T $familyTParam
   */
  inline def sealedParser[T](naming: Macro.DiscriminatorNaming): RowParser[T] =
    ${ sealedParserImpl2[T]('naming) }

  /**
   * $sealedParserDoc
   *
   * @param discriminate $discriminateParam
   * @tparam T $familyTParam
   */
  inline def sealedParser[T](discriminate: Macro.Discriminate): RowParser[T] =
    ${ sealedParserImpl3[T]('discriminate) }

  /**
   * $sealedParserDoc
   *
   * @param naming $discriminatorNamingParam
   * @param discriminate $discriminateParam
   * @tparam T $familyTParam
   */
  inline def sealedParser[T](naming: Macro.DiscriminatorNaming, discriminate: Macro.Discriminate): RowParser[T] =
    ${ sealedParserImpl[T]('naming, 'discriminate) }

  /**
   * Returns a column parser for specified value class.
   *
   * {{{
   * import anorm._
   *
   * class ValueClassType(val v: Int) extends AnyVal
   *
   * implicit val column: Column[ValueClassType] =
   *   Macro.valueColumn[ValueClassType]
   * }}}
   *
   * @tparam T $valueClassTParam
   */
  inline def valueColumn[T <: AnyVal]: Column[T] =
    ??? // TODO: macro anorm.macros.ValueColumnImpl[T]

  // --- ToParameter ---

  // TODO: import anorm.macros.ToParameterListImpl

  /**
   * @param separator $separatorParam
   * @tparam T $caseTParam
   *
   * {{{
   * import anorm.{ Macro, ToParameterList }
   *
   * case class Bar(v: Float)
   *
   * // Bar must be a case class, or a sealed trait with known subclasses
   * implicit val toParams: ToParameterList[Bar] = Macro.toParameters[Bar]
   * }}}
   */
  inline def toParameters[T]: ToParameterList[T] = ${ defaultParameters[T] }

  /**
   * @param separator $separatorParam
   * @tparam T $caseTParam
   *
   * {{{
   * import anorm.{ Macro, ToParameterList }
   *
   * case class Bar(v: String)
   *
   * // Bar must be a case class
   * implicit val toParams: ToParameterList[Bar] =
   *   Macro.toParameters[Bar]("_")
   * }}}
   */
  inline def toParameters[T](separator: String): ToParameterList[T] =
    ${ parametersDefaultNames[T]('separator) }

  /**
   * @param projection $projectionParam
   * @tparam T $caseTParam
   *
   * {{{
   * import anorm.{ Macro, ToParameterList }
   *
   * case class Bar(v: Int)
   *
   * // Bar must be a case class
   * implicit val toParams: ToParameterList[Bar] =
   *   Macro.toParameters[Bar]()
   * }}}
   */
  inline def toParameters[T](projection: Macro.ParameterProjection*): ToParameterList[T] = ${
    configuredParameters[T]('projection)
  }

  /**
   * @param separator $separatorParam
   * @param projection $projectionParam
   * @tparam T $caseTParam
   */
  inline def toParameters[T](separator: String, projection: Macro.ParameterProjection*): ToParameterList[T] =
    ${ parametersWithSeparator[T]('separator, 'projection) }

  /**
   * Returns a `ToStatement` for the specified ValueClass.
   *
   * {{{
   * import anorm._
   *
   * class ValueClassType(val i: Int) extends AnyVal
   *
   * implicit val instance: ToStatement[ValueClassType] =
   *   Macro.valueToStatement[ValueClassType]
   * }}}
   *
   * @tparam T $valueClassTParam
   */
  inline def valueToStatement[T <: AnyVal]: ToStatement[T] =
    ??? // TODO: macro anorm.macros.ValueToStatement[T]

  // ---

  private def namedParserImpl[T](using Quotes, Type[T]): Expr[RowParser[T]] =
    withColumn[T] { col =>
      parserImpl[T] { (n, _) =>
        '{ anorm.SqlParser.get[T](${ Expr(n) })($col) }
      }
    }

  private def namedParserImpl1[T](
      naming: Expr[ColumnNaming]
  )(using Quotes, Type[T], Type[Macro.ColumnNaming]): Expr[RowParser[T]] =
    withColumn[T] { col =>
      parserImpl[T] { (n, _) =>
        '{ anorm.SqlParser.get[T]($naming(${ Expr(n) }))($col) }
      }
    }

  private def namedParserImpl2[T](names: Expr[Seq[String]])(using Quotes, Type[T]): Expr[RowParser[T]] =
    namedParserImpl4[T](names)(identity)

  private def namedParserImpl3[T](naming: Expr[ColumnNaming], names: Expr[Seq[String]])(using
      Quotes,
      Type[T],
      Type[Macro.ColumnNaming]
  ): Expr[RowParser[T]] =
    namedParserImpl4[T](names) { n => '{ $naming($n) } }

  private def namedParserImpl4[T](
      names: Expr[Seq[String]]
  )(naming: Expr[String] => Expr[String])(using q: Quotes, tpe: Type[T]): Expr[RowParser[T]] = {
    import q.reflect.*

    val repr = TypeRepr.of[T](using tpe)
    val ctor = repr.typeSymbol.primaryConstructor

    val params = ctor.paramSymss.flatten

    @SuppressWarnings(Array("ListSize"))
    def psz = params.size

    val ns = names.valueOrAbort

    if (ns.size < psz) {
      report.errorAndAbort(s"no column name for parameters: ${ns.mkString(", ")} < $params")

    } else {
      parserImpl[T] { (_, i) =>
        ns.lift(i) match {
          case Some(n) =>
            withColumn[T] { col =>
              val cn = naming(Expr(n))
              '{ anorm.SqlParser.get[T]($cn)($col) }
            }

          case _ =>
            report.errorAndAbort(s"missing column name for parameter $i")
        }
      }
    }
  }

  private def offsetParserImpl[T](offset: Expr[Int])(using Quotes, Type[T]): Expr[RowParser[T]] =
    withColumn[T] { col =>
      parserImpl[T] { (_, i) =>
        '{ anorm.SqlParser.get[T]($offset + ${ Expr(i + 1) })($col) }
      }
    }

  private def indexedParserImpl[T](using Quotes, Type[T]): Expr[RowParser[T]] =
    offsetParserImpl[T]('{ 0 })

  private def sealedParserImpl1[T](using Quotes, Type[T]): Expr[RowParser[T]] = {
    def discriminator = '{ anorm.Macro.DiscriminatorNaming.Default }
    def discriminate  = '{ anorm.Macro.Discriminate.Identity }

    sealedParserImpl(discriminator, discriminate)
  }

  private def sealedParserImpl2[T](naming: Expr[DiscriminatorNaming])(using Quotes, Type[T]): Expr[RowParser[T]] =
    sealedParserImpl(naming, '{ anorm.Macro.Discriminate.Identity })

  private def sealedParserImpl3[T](discriminate: Expr[Discriminate])(using Quotes, Type[T]): Expr[RowParser[T]] =
    sealedParserImpl('{ anorm.Macro.DiscriminatorNaming.Default }, discriminate)

  private def sealedParserImpl[T](naming: Expr[DiscriminatorNaming], discriminate: Expr[Discriminate])(using
      Quotes,
      Type[T]
  ): Expr[RowParser[T]] =
    '{ ??? } // TODO: anorm.macros.SealedRowParserImpl[T](c)(naming, discriminate)

  private def parserImpl[T](genGet: (String, Int) => Expr[RowParser[T]])(using Quotes, Type[T]): Expr[RowParser[T]] = {
    // TODO: anorm.macros.RowParserImpl[T](c)(genGet)
    '{ ??? }
  }

  private def defaultParameters[T](using Quotes, Type[T]): Expr[ToParameterList[T]] = {
    /* TODO
    val tpe    = c.weakTypeTag[T].tpe
    val tpeSym = tpe.typeSymbol.asClass

    @inline def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    if (tpeSym.isSealed && tpeSym.isAbstract) {
      ToParameterListImpl.sealedTrait[T](c)
    } else if (!tpeSym.isClass || !tpeSym.asClass.isCaseClass) {
      abort(s"Either a sealed trait or a case class expected: $tpe")
    } else {
      @silent def p = c.universe.reify("_")

      ToParameterListImpl.caseClass[T](c)(Seq.empty[c.Expr[Macro.ParameterProjection]], p)
    }
     */
    '{ ??? }
  }

  private def parametersDefaultNames[T](separator: Expr[String])(using Quotes, Type[T]): Expr[ToParameterList[T]] =
    '{ ??? } /* TODO: ToParameterListImpl.caseClass[T](
      Seq.empty[c.Expr[Macro.ParameterProjection]], separator) */

  private def configuredParameters[T](
      projection: Expr[Seq[Macro.ParameterProjection]]
  )(using Quotes, Type[T]): Expr[ToParameterList[T]] = {
    /* TODO:
    @silent def p = reify("_")

    ToParameterListImpl.caseClass[T](c)(projection, p)
     */
    '{ ??? }
  }

  private def parametersWithSeparator[T](
      separator: Expr[String],
      projection: Expr[Seq[Macro.ParameterProjection]]
  )(using Quotes, Type[T]): Expr[ToParameterList[T]] =
    '{ ??? } // TODO: ToParameterListImpl.caseClass[T](c)(projection, separator)

  private def withColumn[T](
      f: Expr[Column[T]] => Expr[RowParser[T]]
  )(using q: Quotes, tpe: Type[T]): Expr[RowParser[T]] = {
    import q.reflect.*

    Expr.summon[Column[T]] match {
      case Some(col) =>
        f(col)

      case _ => {
        val repr = TypeRepr.of[T](using tpe)

        report.errorAndAbort(s"Missing Column[${repr.show}]")
      }
    }
  }

end Macro

/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import scala.quoted.{ Expr, FromExpr, Quotes, ToExpr, Type }

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
object Macro extends MacroOptions with macros.ValueColumn with macros.ValueToStatement:

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
  inline def namedParser[T](inline naming: ColumnNaming): RowParser[T] =
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
  inline def parser[T](inline names: String*): RowParser[T] =
    ${ namedParserImpl3[T]('names) }

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
  inline def parser[T](inline naming: ColumnNaming, inline names: String*): RowParser[T] =
    ${ namedParserImpl2[T]('naming, 'names) }

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
  inline def offsetParser[T](inline offset: Int): RowParser[T] =
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
  inline def sealedParser[T](inline naming: DiscriminatorNaming): RowParser[T] =
    ${ sealedParserImpl2[T]('naming) }

  /**
   * $sealedParserDoc
   *
   * @param discriminate $discriminateParam
   * @tparam T $familyTParam
   */
  inline def sealedParser[T](inline discriminate: Discriminate): RowParser[T] =
    ${ sealedParserImpl3[T]('discriminate) }

  /**
   * $sealedParserDoc
   *
   * @param naming $discriminatorNamingParam
   * @param discriminate $discriminateParam
   * @tparam T $familyTParam
   */
  inline def sealedParser[T](inline naming: DiscriminatorNaming, discriminate: Discriminate): RowParser[T] =
    ${ sealedParserImpl[T]('naming, 'discriminate) }

  // ---

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
    ${ valueColumnImpl[T] }

  // ---

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
  inline def toParameters[T](inline separator: String): ToParameterList[T] =
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
  inline def toParameters[T](inline projection: ParameterProjection*): ToParameterList[T] = ${
    configuredParameters[T]('projection)
  }

  /**
   * @param separator $separatorParam
   * @param projection $projectionParam
   * @tparam T $caseTParam
   */
  inline def toParameters[T](inline separator: String, projection: ParameterProjection*): ToParameterList[T] = ${
    parametersWithSeparator[T]('separator, 'projection)
  }

  // ---

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
    ${ valueToStatementImpl[T] }

  // ---

  private def namedParserImpl[A](using q: Quotes, tpe: Type[A]): Expr[RowParser[A]] = {
    parserImpl[A](q) {
      [T] => (_: Type[T]) ?=> (col: Expr[Column[T]], n: String, _: Int) => '{ SqlParser.get[T](${ Expr(n) })($col) }
    }
  }

  private def namedParserImpl1[A](
      naming: Expr[ColumnNaming]
  )(using q: Quotes, tpe: Type[A], colNme: Type[ColumnNaming]): Expr[RowParser[A]] = {
    parserImpl[A](q) {
      [T] =>
        (_: Type[T]) ?=> (col: Expr[Column[T]], n: String, _: Int) => '{ SqlParser.get[T]($naming(${ Expr(n) }))($col) }
    }
  }

  private def namedParserImpl2[T](
      naming: Expr[ColumnNaming],
      names: Expr[Seq[String]]
  )(using
      Quotes,
      Type[T],
      Type[ColumnNaming]
  ): Expr[RowParser[T]] =
    namedParserImpl4[T](names) { n => '{ $naming($n) } }

  private def namedParserImpl3[T](names: Expr[Seq[String]])(using
      Quotes,
      Type[T],
      Type[ColumnNaming]
  ): Expr[RowParser[T]] = namedParserImpl4[T](names)(identity)

  private def namedParserImpl4[A](
      names: Expr[Seq[String]]
  )(naming: Expr[String] => Expr[String])(using q: Quotes, tpe: Type[A]): Expr[RowParser[A]] = {

    import q.reflect.*

    val repr = TypeRepr.of[A](using tpe)
    val ctor = repr.typeSymbol.primaryConstructor

    val params = ctor.paramSymss.map(_.filterNot(_.isType)).flatten

    @SuppressWarnings(Array("ListSize"))
    def psz = params.size

    val ns = names.valueOrAbort

    if (ns.size < psz) {
      report.errorAndAbort(s"no column name for parameters: ${ns.mkString(", ")} < ${params.mkString("[", ", ", "]")}")

    } else {
      parserImpl[A](q) {
        [T] =>
          (_: Type[T]) ?=>
            (col: Expr[Column[T]], _: String, i: Int) =>
              ns.lift(i) match {
                case Some(n) => {
                  val cn = naming(Expr(n))

                  '{ SqlParser.get[T]($cn)($col) }
                }

                case _ =>
                  report.errorAndAbort(s"missing column name for parameter $i")
            }
      }
    }
  }

  private def offsetParserImpl[A](offset: Expr[Int])(using q: Quotes, tpe: Type[A]): Expr[RowParser[A]] = {
    parserImpl[A](q) {
      [T] =>
        (_: Type[T]) ?=>
          (col: Expr[Column[T]], _: String, i: Int) => '{ SqlParser.get[T]($offset + ${ Expr(i + 1) })($col) }
    }
  }

  private def indexedParserImpl[T](using Quotes, Type[T]): Expr[RowParser[T]] =
    offsetParserImpl[T]('{ 0 })

  private def sealedParserImpl1[T](using Quotes, Type[T]): Expr[RowParser[T]] = {
    def discriminator = '{ Macro.DiscriminatorNaming.Default }
    def discriminate  = '{ Macro.Discriminate.Identity }

    sealedParserImpl(discriminator, discriminate)
  }

  private def sealedParserImpl2[T](naming: Expr[DiscriminatorNaming])(using Quotes, Type[T]): Expr[RowParser[T]] =
    sealedParserImpl(naming, '{ Macro.Discriminate.Identity })

  private def sealedParserImpl3[T](discriminate: Expr[Discriminate])(using Quotes, Type[T]): Expr[RowParser[T]] =
    sealedParserImpl('{ Macro.DiscriminatorNaming.Default }, discriminate)

  private def sealedParserImpl[T](naming: Expr[DiscriminatorNaming], discriminate: Expr[Discriminate])(using
      Quotes,
      Type[T]
  ): Expr[RowParser[T]] =
    macros.SealedRowParserImpl[T](naming, discriminate)

  inline private def withParser[T](f: RowParser[T] => (Row => SqlResult[T])): RowParser[T] = new RowParser[T] { self =>
    lazy val underlying = f(self)

    def apply(row: Row): SqlResult[T] = underlying(row)
  }

  /**
   * @tparam T the field type
   */
  private[anorm] type RowParserGenerator =
    [T] => (fieldType: Type[T]) ?=> (column: Expr[Column[T]], fieldName: String, fieldIndex: Int) => Expr[RowParser[T]]

  /**
   * @tparam A the case class type
   * @param genGet the function applied to each field of case class `A`
   */
  private def parserImpl[A](q: Quotes)(genGet: RowParserGenerator)(using Type[A]): Expr[RowParser[A]] = {
    given quotes: Quotes = q

    '{
      withParser[A] { self =>
        ${ macros.RowParserImpl[A](q, 'self)(genGet) }
      }
    }
  }

  // ---

  private[anorm] given parameterProjectionFromExpr: FromExpr[ParameterProjection] = new FromExpr[ParameterProjection] {
    def unapply(expr: Expr[ParameterProjection])(using q: Quotes): Option[ParameterProjection] = {
      import q.reflect.*

      val strTpr = TypeRepr.of[String]

      @annotation.tailrec
      def rec(term: Term): Option[ParameterProjection] = term match {
        case Block(stats, e) =>
          if stats.isEmpty then rec(e) else None

        case Inlined(_, bindings, e) =>
          if bindings.isEmpty then rec(e) else None

        case Typed(e, _) =>
          rec(e)

        case Apply(meth, propNme :: paramNme :: Nil)
            if meth.symbol.fullName.endsWith(f"ParameterProjection$$.apply") => {
          val strFrom = FromExpr.StringFromExpr[String]

          for {
            propertyName <- strFrom.unapply(propNme.asExprOf[String])
            parameterName <- {
              if (paramNme.tpe <:< strTpr) {
                strFrom.unapply(paramNme.asExprOf[String]).map(Option(_))
              } else {
                FromExpr.OptionFromExpr[String].unapply(paramNme.asExprOf[Option[String]])
              }
            }
          } yield ParameterProjection(propertyName, parameterName)
        }

        case _ =>
          None
      }

      rec(expr.asTerm)
    }
  }

  private[anorm] given parameterProjectionToExpr: ToExpr[ParameterProjection] =
    new ToExpr[ParameterProjection] {
      def apply(p: ParameterProjection)(using q: Quotes): Expr[ParameterProjection] = {
        import q.reflect.*

        val propertyName  = Expr(p.propertyName)
        val parameterName = Expr(p.parameterName)

        '{ Macro.ParameterProjection($propertyName, $parameterName) }
      }
    }

  private def parametersDefaultNames[T](separator: Expr[String])(using
      q: Quotes,
      tpe: Type[T],
      proj: Type[ParameterProjection]
  ): Expr[ToParameterList[T]] = {
    import q.reflect.*

    '{
      withSelfToParameterList[T] { selfRef =>
        ${
          macros.ToParameterListImpl.caseClass[T](
            forwardExpr = 'selfRef,
            projection = Expr(Seq.empty[ParameterProjection]),
            separator = separator
          )
        }
      }
    }
  }

  private def configuredParameters[T](
      projection: Expr[Seq[ParameterProjection]]
  )(using q: Quotes, tpe: Type[T], proj: Type[ParameterProjection]): Expr[ToParameterList[T]] = {
    import q.reflect.*

    '{
      withSelfToParameterList[T] { selfRef =>
        ${ macros.ToParameterListImpl.caseClass[T]('selfRef, projection, '{ "_" }) }
      }
    }
  }

  private def parametersWithSeparator[T](
      separator: Expr[String],
      projection: Expr[Seq[ParameterProjection]]
  )(using q: Quotes, tpe: Type[T]): Expr[ToParameterList[T]] = {
    import q.reflect.*

    '{
      withSelfToParameterList[T] { selfRef =>
        ${ macros.ToParameterListImpl.caseClass[T]('selfRef, projection, separator) }
      }
    }
  }

  private def defaultParameters[T](using
      q: Quotes,
      tpe: Type[T],
      proj: Type[ParameterProjection]
  ): Expr[ToParameterList[T]] = {
    import q.reflect.*

    val repr   = TypeRepr.of[T](using tpe)
    val tpeSym = repr.typeSymbol
    val flags  = tpeSym.flags

    if (flags.is(Flags.Sealed) || flags.is(Flags.Abstract)) {
      macros.ToParameterListImpl.sealedTrait[T]
    } else if (!tpeSym.isClassDef || !flags.is(Flags.Case)) {
      report.errorAndAbort(s"Either a sealed trait or a case class expected: $tpe")

    } else {
      '{
        withSelfToParameterList[T] { selfRef =>
          ${
            macros.ToParameterListImpl.caseClass[T](
              forwardExpr = 'selfRef,
              projection = Expr(Seq.empty[ParameterProjection]),
              separator = '{ "_" }
            )
          }
        }
      }
    }
  }

  // ---

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

  inline private def withSelfToParameterList[T](
      f: ToParameterList[T] => (T => List[NamedParameter])
  ): ToParameterList[T] = new ToParameterList[T] { self =>
    lazy val underlying = f(self)

    def apply(input: T): List[NamedParameter] = underlying(input)
  }

  /** Only for internal purposes */
  final class Placeholder {}

  /** Only for internal purposes */
  object Placeholder {
    implicit object Parser extends RowParser[Placeholder] {
      val success = Success(new Placeholder())

      def apply(row: Row) = success
    }
  }

  private[anorm] lazy val debugEnabled =
    Option(System.getProperty("anorm.macro.debug"))
      .filterNot(_.isEmpty)
      .map(_.toLowerCase)
      .map { v =>
        "true".equals(v) || v.substring(0, 1) == "y"
      }
      .getOrElse(false)

end Macro

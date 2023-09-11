/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import com.github.ghik.silencer.silent

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
object Macro extends MacroOptions {
  import scala.language.experimental.macros
  import scala.reflect.macros.whitebox

  def namedParserImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[RowParser[T]] = {
    import c.universe._

    parserImpl[T](c) { (t, n, _) => q"anorm.SqlParser.get[$t]($n)" }
  }

  def namedParserImpl1[T: c.WeakTypeTag](c: whitebox.Context)(naming: c.Expr[ColumnNaming]): c.Expr[RowParser[T]] = {
    import c.universe._

    parserImpl[T](c) { (t, n, _) => q"anorm.SqlParser.get[$t]($naming($n))" }
  }

  @deprecated("Use [[namedParserImpl2]]", "2.5.2")
  @SuppressWarnings(Array("MethodNames" /*deprecated*/ ))
  def namedParserImpl_[T: c.WeakTypeTag](c: whitebox.Context)(names: c.Expr[String]*): c.Expr[RowParser[T]] =
    namedParserImpl2[T](c)(names: _*)

  def namedParserImpl2[T: c.WeakTypeTag](c: whitebox.Context)(names: c.Expr[String]*): c.Expr[RowParser[T]] = {
    import c.universe._

    namedParserImpl4[T](c)(names) { n => q"$n" }
  }

  def namedParserImpl3[T: c.WeakTypeTag](
      c: whitebox.Context
  )(naming: c.Expr[ColumnNaming], names: c.Expr[String]*): c.Expr[RowParser[T]] = {
    import c.universe._

    namedParserImpl4[T](c)(names) { n => q"$naming($n)" }
  }

  private def namedParserImpl4[T: c.WeakTypeTag](
      c: whitebox.Context
  )(names: Seq[c.Expr[String]])(naming: c.Expr[String] => c.universe.Tree): c.Expr[RowParser[T]] = {
    import c.universe._

    val tpe    = c.weakTypeTag[T].tpe
    val ctor   = tpe.decl(termNames.CONSTRUCTOR).asMethod
    val params = ctor.paramLists.flatten

    @SuppressWarnings(Array("ListSize"))
    def psz = params.size

    if (names.size < psz) {
      c.abort(
        c.enclosingPosition,
        s"no column name for parameters: ${names.map(n => show(n)).mkString(", ")} < ${params.map(_.name).mkString(", ")}"
      )

    } else {
      parserImpl[T](c) { (t, _, i) =>
        names.lift(i) match {
          case Some(n) => {
            val cn = naming(n)
            q"anorm.SqlParser.get[$t]($cn)"
          }

          case _ => c.abort(c.enclosingPosition, s"missing column name for parameter $i")
        }
      }
    }
  }

  def offsetParserImpl[T: c.WeakTypeTag](c: whitebox.Context)(offset: c.Expr[Int]): c.Expr[RowParser[T]] = {
    import c.universe._

    parserImpl[T](c) { (t, _, i) =>
      q"anorm.SqlParser.get[$t]($offset + ${i + 1})"
    }
  }

  def indexedParserImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[RowParser[T]] = {
    import c.universe._

    @silent def p = reify(0)

    offsetParserImpl[T](c)(p)
  }

  def sealedParserImpl1[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[RowParser[T]] = {
    import c.universe.reify

    @silent def discriminator = reify(DiscriminatorNaming.Default)
    @silent def discriminate  = reify(Discriminate.Identity)

    sealedParserImpl(c)(discriminator, discriminate)
  }

  def sealedParserImpl2[T: c.WeakTypeTag](c: whitebox.Context)(
      naming: c.Expr[DiscriminatorNaming]
  ): c.Expr[RowParser[T]] = sealedParserImpl(c)(naming, c.universe.reify(Discriminate.Identity))

  def sealedParserImpl3[T: c.WeakTypeTag](c: whitebox.Context)(
      discriminate: c.Expr[Discriminate]
  ): c.Expr[RowParser[T]] = sealedParserImpl(c)(c.universe.reify(DiscriminatorNaming.Default), discriminate)

  def sealedParserImpl[T: c.WeakTypeTag](
      c: whitebox.Context
  )(naming: c.Expr[DiscriminatorNaming], discriminate: c.Expr[Discriminate]): c.Expr[RowParser[T]] =
    anorm.macros.SealedRowParserImpl[T](c)(naming, discriminate)

  private def parserImpl[T: c.WeakTypeTag](c: whitebox.Context)(
      genGet: (c.universe.Type, String, Int) => c.universe.Tree
  ): c.Expr[RowParser[T]] = anorm.macros.RowParserImpl[T](c)(genGet)

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
  def namedParser[T]: RowParser[T] = macro namedParserImpl[T]

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
   * import anorm.{ Macro, RowParser }
   *
   * case class YourCaseClass(a: Int, b: String)
   *
   * val p: RowParser[YourCaseClass] =
   *   Macro.parser[YourCaseClass]("foo", "bar")
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
   * import anorm.{ Macro, RowParser }
   *
   * case class YourCaseClass(a: Int, b: String)
   *
   * val p: RowParser[YourCaseClass] =
   *   Macro.parser[YourCaseClass]("foo", "loremIpsum")
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
   * import anorm.{ Macro, RowParser }
   *
   * case class YourCaseClass(v: Int)
   *
   * val p: RowParser[YourCaseClass] = Macro.indexedParser[YourCaseClass]
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
   * import anorm.{ Macro, RowParser }
   *
   * case class YourCaseClass(v: Int)
   *
   * val p: RowParser[YourCaseClass] = Macro.offsetParser[YourCaseClass](2)
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
  def sealedParser[T](naming: Macro.DiscriminatorNaming, discriminate: Macro.Discriminate): RowParser[T] =
    macro sealedParserImpl[T]

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
  def valueColumn[T <: AnyVal]: Column[T] = macro anorm.macros.ValueColumnImpl[T]

  // --- ToParameter ---

  import anorm.macros.ToParameterListImpl

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
  def toParameters[T]: ToParameterList[T] = macro defaultParameters[T]

  def defaultParameters[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[ToParameterList[T]] = {
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
  }

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
  @SuppressWarnings(Array("UnusedMethodParameter" /*macro*/ ))
  def toParameters[T](separator: String): ToParameterList[T] = macro parametersDefaultNames[T]

  def parametersDefaultNames[T: c.WeakTypeTag](
      c: whitebox.Context
  )(separator: c.Expr[String]): c.Expr[ToParameterList[T]] =
    ToParameterListImpl.caseClass[T](c)(Seq.empty[c.Expr[Macro.ParameterProjection]], separator)

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
  @SuppressWarnings(Array("UnusedMethodParameter" /*macro*/ ))
  def toParameters[T](projection: Macro.ParameterProjection*): ToParameterList[T] = macro configuredParameters[T]

  def configuredParameters[T: c.WeakTypeTag](
      c: whitebox.Context
  )(projection: c.Expr[Macro.ParameterProjection]*): c.Expr[ToParameterList[T]] = {
    import c.universe.reify

    @silent def p = reify("_")

    ToParameterListImpl.caseClass[T](c)(projection, p)
  }

  /**
   * @param separator $separatorParam
   * @param projection $projectionParam
   * @tparam T $caseTParam
   */
  @SuppressWarnings(Array("UnusedMethodParameter" /*macro*/ ))
  def toParameters[T](separator: String, projection: Macro.ParameterProjection*): ToParameterList[T] =
    macro parametersWithSeparator[T]

  def parametersWithSeparator[T: c.WeakTypeTag](
      c: whitebox.Context
  )(separator: c.Expr[String], projection: c.Expr[Macro.ParameterProjection]*): c.Expr[ToParameterList[T]] =
    ToParameterListImpl.caseClass[T](c)(projection, separator)

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
  def valueToStatement[T <: AnyVal]: ToStatement[T] = macro anorm.macros.ValueToStatement[T]

  // ---

  /** Only for internal purposes */
  final class Placeholder {}

  /** Only for internal purposes */
  object Placeholder {
    implicit object Parser extends RowParser[Placeholder] {
      val success = Success(new Placeholder())

      def apply(row: Row): anorm.Success[anorm.Macro.Placeholder] = success
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

}

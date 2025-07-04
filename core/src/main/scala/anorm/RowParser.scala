/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

trait RowParser[+A] extends (Row => SqlResult[A]) { parent =>

  /**
   * Returns a parser that will apply given function `f`
   * to the result of this first parser. If the current parser is not
   * successful, the new one will return encountered [[Error]].
   *
   * @param f Function applied on the successful parser result
   *
   * {{{
   * import anorm.{ RowParser, SqlParser }
   *
   * val parser: RowParser[Int] = SqlParser.str("col").map(_.length)
   * // Prepares a parser that first get 'col' string value,
   * // and then returns the length of that
   * }}}
   */
  def map[B](f: A => B): RowParser[B] = RowParser(parent.andThen(_.map(f)))

  /**
   * Returns parser which collects information
   * from already parsed row data using `f`.
   *
   * @param otherwise Message returned as error if nothing can be collected using `f`.
   * @param f Collecting function
   */
  def collect[B](otherwise: String)(f: PartialFunction[A, B]): RowParser[B] =
    RowParser(parent(_).flatMap(f.lift(_).fold[SqlResult[B]](Error(SqlMappingError(otherwise)))(Success(_))))

  def flatMap[B](k: A => RowParser[B]): RowParser[B] =
    RowParser(row => parent(row).flatMap(k(_)(row)))

  /**
   * Combines this parser on the left of the parser `p` given as argument.
   *
   * @param p Parser on the right
   *
   * {{{
   * import anorm._, SqlParser.{ int, str }
   *
   * def populations(implicit con: java.sql.Connection): List[String ~ Int] =
   *   SQL("SELECT * FROM Country").as((str("name") ~ int("population")).*)
   * }}}
   */
  def ~[B](p: RowParser[B]): RowParser[A ~ B] =
    RowParser(row => parent(row).flatMap(a => p(row).map(new ~(a, _))))

  /**
   * Combines this current parser with the one given as argument `p`,
   * if and only if the current parser can first/on left side successfully
   * parse a row, without keeping these values in parsed result.
   *
   * {{{
   * import anorm._, SqlParser.{ int, str }
   *
   * def string(implicit con: java.sql.Connection) = SQL("SELECT * FROM test").
   *   as((int("id") ~> str("val")).single)
   * // row has to have an int column 'id' and a string 'val' one,
   * // keeping only 'val' in result
   * }}}
   */
  def ~>[B](p: RowParser[B]): RowParser[B] =
    RowParser(row => parent(row).flatMap(_ => p(row)))

  /**
   * Combines this current parser with the one given as argument `p`,
   * if and only if the current parser can first successfully
   * parse a row, without keeping the values of the parser `p`.
   *
   * {{{
   * import anorm._, SqlParser.{ int, str }
   *
   * def i(implicit con: java.sql.Connection) = SQL("SELECT * FROM test").
   *   as((int("id") <~ str("val")).single)
   * // row has to have an int column 'id' and a string 'val' one,
   * // keeping only 'id' in result
   * }}}
   */
  def <~[B](p: RowParser[B]): RowParser[A] = parent.~(p).map(_._1)

  // TODO: Scaladoc
  def |[B >: A](p: RowParser[B]): RowParser[B] = RowParser { row =>
    parent(row) match {
      case Error(_) => p(row)
      case a        => a
    }
  }

  /**
   * Returns a row parser for optional column,
   * that will turn missing or null column as None.
   */
  def ? : RowParser[Option[A]] = RowParser {
    parent(_) match {
      case Success(a)                  => Success(Some(a))
      case Error(ColumnNotFound(_, _)) =>
        Success(None)

      case e @ Error(_) => e
    }
  }

  /** Alias for [[flatMap]] */
  def >>[B](f: A => RowParser[B]): RowParser[B] = flatMap(f)

  /**
   * Returns possibly empty list parsed from result.
   *
   * {{{
   * import anorm._, SqlParser.scalar
   *
   * val price = 125
   *
   * def foo(implicit con: java.sql.Connection) =
   *   SQL"SELECT name FROM item WHERE price < \\$price".as(scalar[String].*)
   * }}}
   */
  def * : ResultSetParser[List[A]] = ResultSetParser.list(parent)

  /**
   * Returns non empty list parse from result,
   * or raise error if there is no result.
   *
   * {{{
   * import anorm._, SqlParser.str
   *
   * def foo(implicit con: java.sql.Connection) = {
   *   val parser = str("title") ~ str("descr")
   *   SQL("SELECT title, descr FROM pages").as(parser.+) // at least 1 page
   * }
   * }}}
   */
  def + : ResultSetParser[List[A]] = ResultSetParser.nonEmptyList(parent)

  /**
   * Returns a result set parser expecting exactly one row to parse.
   *
   * {{{
   * import anorm._, SqlParser.scalar
   *
   * def b(implicit con: java.sql.Connection): Boolean =
   *   SQL("SELECT flag FROM Test WHERE id = :id").
   *     on("id" -> 1).as(scalar[Boolean].single)
   * }}}
   *
   * @see #singleOpt
   */
  def single = ResultSetParser.single(parent)

  /**
   * Returns a result set parser for none or one parsed row.
   *
   * {{{
   * import anorm._, SqlParser.scalar
   *
   * def name(implicit con: java.sql.Connection): Option[String] =
   *   SQL("SELECT name FROM Country WHERE lang = :lang")
   *     .on("lang" -> "notFound").as(scalar[String].singleOpt)
   * }}}
   */
  def singleOpt: ResultSetParser[Option[A]] = ResultSetParser.singleOpt(parent)

}

object RowParser {
  def apply[A](f: Row => SqlResult[A]): RowParser[A] = new RowParser[A] {
    def apply(row: Row): SqlResult[A] = f(row)
  }

  /** Row parser that result in successfully unchanged row. */
  object successful extends RowParser[Row] {
    def apply(row: Row): SqlResult[Row] = Success(row)
  }

  def failed[A](error: => Error): RowParser[A] = new RowParser[A] {
    def apply(row: Row): SqlResult[A] = error
  }
}

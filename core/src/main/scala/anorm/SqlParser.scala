/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import scala.collection.immutable.Seq

object SqlParser extends FunctionAdapter with DeprecatedSqlParser {
  import java.io.InputStream
  import java.util.Date

  private val NoColumnsInReturnedResult = SqlMappingError("No column in result")

  /**
   * Returns a parser for a scalar not-null value.
   *
   * {{{
   * import anorm._, SqlParser.scalar
   *
   * def count(implicit con: java.sql.Connection) =
   *   SQL("select count(*) from Country").as(scalar[Long].single)
   * }}}
   */
  def scalar[T](implicit @deprecatedName(Symbol("transformer")) c: Column[T]): RowParser[T] =
    new ScalarRowParser[T] {
      def apply(row: Row): SqlResult[T] = {
        val input: Either[SqlMappingError, (Any, MetaDataItem)] = (for {
          m <- row.metaData.ms.headOption
          v <- row.data.headOption
        } yield v -> m).toRight(NoColumnsInReturnedResult)

        val parsed = Compat.rightFlatMap[SqlMappingError, SqlRequestError, (Any, MetaDataItem), T](input) {
          case in @ (_, m) =>
            parseColumn(row, m.column.qualified, c, in)
        }

        parsed.fold(Error(_), Success(_))
      }
    }

  /**
   * Returns a parser that fold over the row.
   *
   * {{{
   * import anorm._
   *
   * def p(implicit con: java.sql.Connection): RowParser[List[(Any, String)]] =
   *   SqlParser.folder(List.empty[(Any, String)]) { (ls, v, m) =>
   *     Right((v, m.clazz) :: ls)
   *   }
   * }}}
   */
  def folder[T](z: T)(f: (T, Any, MetaDataItem) => Either[SqlRequestError, T]): RowParser[T] = {
    @annotation.tailrec
    def go(data: List[Any], meta: Seq[MetaDataItem], out: T): SqlResult[T] =
      (data.headOption, meta.headOption) match {
        case (Some(d), Some(m)) =>
          f(out, d, m) match {
            case Left(err)  => Error(err)
            case Right(res) => go(data.tail, meta.tail, res)
          }
        case _ => Success(out)
      }

    RowParser[T] { row => go(row.data, row.metaData.ms, z) }
  }

  /**
   * Flatten columns tuple-like.
   *
   * {{{
   * import anorm._, SqlParser.{ long, str, int }
   *
   * def tuple(implicit con: java.sql.Connection): (Long, String, Int) =
   *   SQL("SELECT a, b, c FROM Test").
   *     as((long("a") ~ str("b") ~ int("c")).map(SqlParser.flatten).single)
   * }}}
   */
  def flatten[T1, T2, R](implicit f: TupleFlattener[(T1 ~ T2) => R]): ((T1 ~ T2) => R) = f.f

  /**
   * Parses specified array column.
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (String, Array[String]) =
   *   SQL"SELECT a, sqlArrayOfString FROM test".as(
   *     (SqlParser.str("a") ~ SqlParser.array[String](
   *       "sqlArrayOfString")).map(SqlParser.flatten).single)
   * }}}
   */
  def array[T](columnName: String)(implicit c: Column[Array[T]]): RowParser[Array[T]] = get[Array[T]](columnName)(c)

  /**
   * Parses specified array column.
   * @param columnPosition from 1 to n
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (String, Array[String]) =
   *   SQL"SELECT a, sqlArrayOfString FROM test".as(
   *     (SqlParser.str("a") ~ SqlParser.array[String](2)).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def array[T](columnPosition: Int)(implicit c: Column[Array[T]]): RowParser[Array[T]] =
    get[Array[T]](columnPosition)(c)

  /**
   * Parses specified column as float.
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Float, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.float("a") ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def float(columnName: String)(implicit c: Column[Float]): RowParser[Float] =
    get[Float](columnName)(c)

  /**
   * Parses specified column as float.
   * @param columnPosition from 1 to n
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Float, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.float(1) ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def float(columnPosition: Int)(implicit c: Column[Float]): RowParser[Float] =
    get[Float](columnPosition)(c)

  /**
   * Parses specified column as string.
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Float, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.float("a") ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def str(columnName: String)(implicit c: Column[String]): RowParser[String] =
    get[String](columnName)(c)

  /**
   * Parses specified column as string.
   * @param columnPosition from 1 to n
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Float, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.float("a") ~ SqlParser.str(1)).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def str(columnPosition: Int)(implicit c: Column[String]): RowParser[String] =
    get[String](columnPosition)(c)

  /**
   * Parses specified column as binary stream.
   *
   * {{{
   * import java.io.InputStream
   * import anorm._
   *
   * val parser = (SqlParser.str("name") ~ SqlParser.binaryStream("data")).
   *   map(SqlParser.flatten)
   *
   * def file(implicit con: java.sql.Connection): (String, InputStream) =
   *   SQL("SELECT name, data FROM files").as(parser.single)
   * }}}
   */
  def binaryStream(columnName: String)(implicit c: Column[InputStream]): RowParser[InputStream] =
    get[InputStream](columnName)(c)

  /**
   * Parses specified column as binary stream.
   *
   * {{{
   * import java.io.InputStream
   * import anorm._
   *
   * val parser =
   *   (SqlParser.str(1) ~ SqlParser.binaryStream(2)).map(SqlParser.flatten)
   *
   * def file(implicit con: java.sql.Connection): (String, InputStream) =
   *   SQL("SELECT name, data FROM files").as(parser.single)
   * }}}
   */
  def binaryStream(columnPosition: Int)(implicit c: Column[InputStream]): RowParser[InputStream] =
    get[InputStream](columnPosition)(c)

  /**
   * Parses specified column as boolean.
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Boolean, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.bool("a") ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def bool(columnName: String)(implicit c: Column[Boolean]): RowParser[Boolean] = get[Boolean](columnName)(c)

  /**
   * Parses specified column as boolean.
   * @param columnPosition from 1 to n
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Boolean, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.bool(1) ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def bool(columnPosition: Int)(implicit c: Column[Boolean]): RowParser[Boolean] = get[Boolean](columnPosition)(c)

  /**
   * Parses specified column as byte.
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Byte, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.byte("a") ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def byte(columnName: String)(implicit c: Column[Byte]): RowParser[Byte] =
    get[Byte](columnName)(c)

  /**
   * Parses specified column as byte.
   * @param columnPosition from 1 to n
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Byte, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.byte(1) ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def byte(columnPosition: Int)(implicit c: Column[Byte]): RowParser[Byte] =
    get[Byte](columnPosition)(c)

  /**
   * Parses specified column as binary stream.
   *
   * {{{
   * import anorm._, SqlParser.{ str, byteArray }
   *
   * val parser = (str("name") ~ byteArray("data")).map(SqlParser.flatten)
   *
   * def file(implicit con: java.sql.Connection): (String, Array[Byte]) =
   *   SQL("SELECT name, data FROM files").as(parser.single)
   * }}}
   */
  def byteArray(columnName: String)(implicit c: Column[Array[Byte]]): RowParser[Array[Byte]] =
    get[Array[Byte]](columnName)(c)

  /**
   * Parses specified column as binary stream.
   *
   * {{{
   * import anorm._
   *
   * val parser =
   *   (SqlParser.str(1) ~ SqlParser.byteArray(2)).map(SqlParser.flatten)
   *
   * def file(implicit con: java.sql.Connection): (String, Array[Byte]) =
   *   SQL("SELECT name, data FROM files").as(parser.single)
   * }}}
   */
  def byteArray(columnPosition: Int)(implicit c: Column[Array[Byte]]): RowParser[Array[Byte]] =
    get[Array[Byte]](columnPosition)(c)

  /**
   * Parses specified column as double.
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Double, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.double("a") ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def double(columnName: String)(implicit c: Column[Double]): RowParser[Double] = get[Double](columnName)(c)

  /**
   * Parses specified column as double.
   * @param columnPosition from 1 to n
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Double, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.double(1) ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def double(columnPosition: Int)(implicit c: Column[Double]): RowParser[Double] = get[Double](columnPosition)(c)

  /**
   * Parses specified column as short.
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Short, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.short("a") ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def short(columnName: String)(implicit c: Column[Short]): RowParser[Short] =
    get[Short](columnName)(c)

  /**
   * Parses specified column as short.
   * @param columnPosition from 1 to n
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Short, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.short(1) ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def short(columnPosition: Int)(implicit c: Column[Short]): RowParser[Short] =
    get[Short](columnPosition)(c)

  /**
   * Parses specified column as integer.
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Int, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.int("a") ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def int(columnName: String)(implicit c: Column[Int]): RowParser[Int] =
    get[Int](columnName)(c)

  /**
   * Parses specified column as integer.
   * @param columnPosition from 1 to n
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Int, String) =
   *   SQL("SELECT a, b FROM test")
   *   .as((SqlParser.int(1) ~ SqlParser.str("b")).
   *     map(SqlParser.flatten).single)
   * }}}
   */
  def int(columnPosition: Int)(implicit c: Column[Int]): RowParser[Int] =
    get[Int](columnPosition)(c)

  /**
   * Parses specified array column as list.
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (String, List[String]) =
   *   SQL("SELECT a, sqlArrayOfString FROM test")
   *   .as((SqlParser.str("a") ~ SqlParser.list[String]("sqlArrayOfString")).
   *     map(SqlParser.flatten).single)
   * }}}
   */
  def list[T](columnName: String)(implicit c: Column[List[T]]): RowParser[List[T]] = get[List[T]](columnName)(c)

  /**
   * Parses specified array column as list.
   * @param columnPosition from 1 to n
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (String, List[String]) =
   *   SQL("SELECT a, sqlArrayOfString FROM test")
   *   .as((SqlParser.str("a") ~ SqlParser.list[String](2)).
   *     map(SqlParser.flatten).single)
   * }}}
   */
  def list[T](columnPosition: Int)(implicit c: Column[List[T]]): RowParser[List[T]] = get[List[T]](columnPosition)(c)

  /**
   * Parses specified column as long.
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Long, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.long("a") ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def long(columnName: String)(implicit c: Column[Long]): RowParser[Long] =
    get[Long](columnName)(c)

  /**
   * Parses specified column as long.
   * @param columnPosition from 1 to n
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (Long, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.long(1) ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def long(columnPosition: Int)(implicit c: Column[Long]): RowParser[Long] =
    get[Long](columnPosition)(c)

  /**
   * Parses specified column as date.
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (java.util.Date, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.date("a") ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def date(columnName: String)(implicit c: Column[Date]): RowParser[Date] =
    get[Date](columnName)(c)

  /**
   * Parses specified column as date.
   * @param columnPosition from 1 to n
   *
   * {{{
   * import anorm._
   *
   * def t(implicit con: java.sql.Connection): (java.util.Date, String) =
   *   SQL("SELECT a, b FROM test").as(
   *     (SqlParser.date(1) ~ SqlParser.str("b")).
   *       map(SqlParser.flatten).single)
   * }}}
   */
  def date(columnPosition: Int)(implicit c: Column[Date]): RowParser[Date] =
    get[Date](columnPosition)(c)

  /**
   * Returns row parser for column with given `name`.
   * @param name Column name
   *
   * {{{
   * import anorm._, SqlParser.get
   *
   * def title(implicit con: java.sql.Connection): String =
   *   SQL("SELECT title FROM Books").as(get[String]("title").single)
   * }}}
   */
  def get[T](name: String)(implicit @deprecatedName(Symbol("extractor")) c: Column[T]): RowParser[T] = RowParser {
    row =>
      Compat
        .rightFlatMap(row.get(name)) { in =>
          parseColumn(row, name, c, in)
        }
        .fold(Error(_), Success(_))
  }

  /**
   * Returns row parser for column at given `position`.
   * @param position Column position, from 1 to n
   *
   * {{{
   * import anorm._, SqlParser.get
   *
   * def res(implicit con: java.sql.Connection): (Float, String) =
   *   // parsing columns #1 & #3
   *   SQL("SELECT * FROM Test").as((get[String](1) ~ get[Float](3)).map {
   *     case str ~ f => (f -> str)
   *   }.single)
   * }}}
   */
  def get[T](position: Int)(implicit @deprecatedName(Symbol("extractor")) c: Column[T]): RowParser[T] =
    RowParser { row =>
      Compat
        .rightFlatMap(row.getIndexed(position - 1)) { in =>
          parseColumn(row, in._2.column.qualified, c, in)
        }
        .fold(Error(_), Success(_))
    }

  /**
   * Returns row parser which true if specified `column` is found
   * and matching expected `value`.
   *
   * {{{
   * import anorm._, SqlParser.matches
   *
   * def m(implicit con: java.sql.Connection): Boolean =
   *   SQL("SELECT * FROM table").as(matches("a", 1.2f).single)
   *   // true if column |a| is found and matching 1.2f, otherwise false
   * }}}
   *
   * @return true if matches, or false if not
   */
  def matches[T: Column](column: String, value: T): RowParser[Boolean] =
    get[T](column).?.map(_.fold(false) { _ == value })

  @inline private def parseColumn[T](
      row: Row,
      name: String,
      c: Column[T],
      input: (Any, MetaDataItem)
  ): Either[SqlRequestError, T] = c.tupled(input).left.map {
    case UnexpectedNullableFound(_) =>
      ColumnNotFound(name, row)

    case cause => cause
  }
}

@deprecated("Do not use these combinators", "2.5.4")
sealed trait DeprecatedSqlParser { _parser: SqlParser.type =>

  @deprecated("Use `matches[T]`", "2.5.4")
  @SuppressWarnings(Array("AsInstanceOf"))
  def matches[TT: Column, T <: TT](column: String, value: T)(implicit c: Column[TT]): RowParser[Boolean] =
    get[TT](column)(c).?.map(_.fold(false) {
      _.asInstanceOf[T] == value
    })

}

/** Columns tuple-like */
// Using List or HList?
@SuppressWarnings(Array("ClassNames"))
final case class ~[+A, +B](_1: A, _2: B)

/** Parser for scalar row (row of one single column). */
sealed trait ScalarRowParser[+A] extends RowParser[A] {
  override def singleOpt: ResultSetParser[Option[A]] = ResultSetParser {
    case Some(cur) if cur.next.isEmpty =>
      cur.row.data match {
        case (null :: _) | Nil =>
          // one column present in head row, but column value is null
          Success(Option.empty[A])

        case _ :: _ => map(Some(_))(cur.row)
      }

    case None => Success(Option.empty[A])

    case _ => Error(SqlMappingError("too many rows when expecting a single one"))
  }
}

/** Parses result from the cursor. */
sealed trait ResultSetParser[+A] extends (Option[Cursor] => SqlResult[A]) {
  parent =>
  def map[B](f: A => B): ResultSetParser[B] = ResultSetParser(parent(_).map(f))
}

private[anorm] object ResultSetParser {
  def apply[A](f: Option[Cursor] => SqlResult[A]): ResultSetParser[A] =
    new ResultSetParser[A] { cur =>
      def apply(cur: Option[Cursor]): SqlResult[A] = f(cur)
    }

  def list[A](p: RowParser[A]): ResultSetParser[List[A]] = {
    // Performance note: sequence produces a List in reverse order, since appending to a
    // List is an O(n) operation, and this is done n times, yielding O(n2) just to convert the
    // result set to a List.  Prepending is O(1), so we use prepend, and then reverse the result
    // in the map function below.
    @annotation.tailrec
    def sequence(results: List[A], cur: Option[Cursor]): SqlResult[List[A]] =
      cur match {
        case Some(c) =>
          p(c.row) match {
            case Success(a) => sequence(a :: results, c.next)
            case Error(msg) => Error(msg)
          }
        case _ => Success(results.reverse)
      }

    ResultSetParser { c => sequence(List.empty[A], c) }
  }

  def nonEmptyList[A](p: RowParser[A]): ResultSetParser[List[A]] =
    ResultSetParser(rows =>
      if (rows.isEmpty) Error(SqlMappingError("Empty Result Set"))
      else list(p)(rows)
    )

  def single[A](p: RowParser[A]): ResultSetParser[A] = ResultSetParser {
    case Some(cur) if cur.next.isEmpty => p(cur.row)
    case None                          => Error(SqlMappingError("No rows when expecting a single one"))
    case _                             => Error(SqlMappingError("too many rows when expecting a single one"))

  }

  def singleOpt[A](p: RowParser[A]): ResultSetParser[Option[A]] =
    ResultSetParser {
      case Some(cur) if cur.next.isEmpty => p.map(Some(_))(cur.row)
      case None                          => Success(Option.empty[A])
      case _                             => Error(SqlMappingError("too many rows when expecting a single one"))
    }

}

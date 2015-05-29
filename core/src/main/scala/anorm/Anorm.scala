/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package anorm

import java.util.{ Date, UUID }
import java.sql.{ Connection, PreparedStatement, ResultSet }

import scala.language.postfixOps
import scala.collection.TraversableOnce
import scala.util.Failure

import resource.{ managed, ManagedResource }

/** Error from processing SQL */
sealed trait SqlRequestError {
  def message: String

  /** Returns error as a failure. */
  def toFailure = Failure(sys.error(message))
}

case class ColumnNotFound(
    column: String, possibilities: List[String]) extends SqlRequestError {

  lazy val message = s"$column not found, available columns : " +
    possibilities.map(_.dropWhile(_ == '.')).mkString(", ")

  override lazy val toString = message
}

object ColumnNotFound {
  def apply(column: String, row: Row): ColumnNotFound =
    ColumnNotFound(column, row.metaData.availableColumns)
}

case class UnexpectedNullableFound(message: String) extends SqlRequestError
case class SqlMappingError(reason: String) extends SqlRequestError {
  lazy val message = s"SqlMappingError($reason)"
  override lazy val toString = message
}

case class TypeDoesNotMatch(reason: String) extends SqlRequestError {
  lazy val message = s"TypeDoesNotMatch($reason)"
  override lazy val toString = message
}

/**
 * Untyped value wrapper.
 *
 * {{{
 * SQL("UPDATE t SET val = {o}").on('o -> anorm.Object(val))
 * }}}
 */
case class Object(value: Any)

case class MetaDataItem(column: ColumnName, nullable: Boolean, clazz: String)
case class ColumnName(qualified: String, alias: Option[String])

private[anorm] case class MetaData(ms: List[MetaDataItem]) {
  /** Returns meta data for specified column. */
  def get(columnName: String): Option[MetaDataItem] = {
    val key = columnName.toUpperCase
    dictionary2.get(key).orElse(dictionary.get(key)).
      orElse(aliasedDictionary.get(key))
  }

  private lazy val dictionary: Map[String, MetaDataItem] =
    ms.map(m => m.column.qualified.toUpperCase() -> m).toMap

  private lazy val dictionary2: Map[String, MetaDataItem] =
    ms.map(m => {
      val column = m.column.qualified.split('.').last;
      column.toUpperCase() -> m
    }).toMap

  private lazy val aliasedDictionary: Map[String, MetaDataItem] = {
    ms.flatMap(m => {
      m.column.alias.map(a => Map(a.toUpperCase() -> m)).getOrElse(Map.empty)
    }).toMap
  }

  lazy val columnCount = ms.size

  lazy val availableColumns: List[String] =
    ms.flatMap(i => i.column.qualified :: i.column.alias.toList)

}

/**
 * Wrapper to use [[Seq]] as SQL parameter, with custom formatting.
 *
 * {{{
 * SQL("SELECT * FROM t WHERE %s").
 *   on(SeqParameter(Seq("a", "b"), " OR ", Some("cat = ")))
 * // Will execute as:
 * // SELECT * FROM t WHERE cat = 'a' OR cat = 'b'
 * }}}
 */
sealed trait SeqParameter[A] {
  def values: Seq[A]
  def separator: String
  def before: Option[String]
  def after: Option[String]
}

/** SeqParameter factory */
object SeqParameter {
  def apply[A](
    seq: Seq[A], sep: String = ", ",
    pre: String = "", post: String = ""): SeqParameter[A] =
    new SeqParameter[A] {
      val values = seq
      val separator = sep
      val before = Option(pre)
      val after = Option(post)
    }
}

/** Applied named parameter. */
sealed case class NamedParameter(name: String, value: ParameterValue) {
  lazy val tupled: (String, ParameterValue) = (name, value)
}

/** Companion object for applied named parameter. */
object NamedParameter {
  import scala.language.implicitConversions

  /**
   * Conversion to use tuple, with first element being name
   * of parameter as string.
   *
   * {{{
   * val p: Parameter = ("name" -> 1L)
   * }}}
   */
  implicit def string[V](t: (String, V))(implicit c: V => ParameterValue): NamedParameter = NamedParameter(t._1, c(t._2))

  /**
   * Conversion to use tuple,
   * with first element being symbolic name or parameter.
   *
   * {{{
   * val p: Parameter = ('name -> 1L)
   * }}}
   */
  implicit def symbol[V](t: (Symbol, V))(implicit c: V => ParameterValue): NamedParameter = NamedParameter(t._1.name, c(t._2))

}

private[anorm] trait Sql extends WithResult {
  @deprecated(message = "Use [[preparedStatement]]", since = "2.3.6")
  def getFilledStatement(connection: Connection, getGeneratedKeys: Boolean = false): PreparedStatement

  def preparedStatement(connection: Connection, getGeneratedKeys: Boolean = false): ManagedResource[PreparedStatement]

  /**
   * Executes this statement as query (see [[executeQuery]]) and returns result.
   */
  protected def resultSet(connection: Connection): ManagedResource[ResultSet] =
    preparedStatement(connection) flatMap { stmt =>
      implicit val res = ResultSetResource
      managed(stmt.executeQuery())
    }

  /**
   * Executes this SQL statement.
   * @return true if resultset was returned from execution
   * (statement is query), or false if it executed update.
   *
   * {{{
   * val res: Boolean =
   *   SQL"""INSERT INTO Test(a, b) VALUES(\\${"A"}, \\${"B"}""".execute()
   * }}}
   */
  def execute()(implicit connection: Connection): Boolean =
    preparedStatement(connection).acquireAndGet(_.execute())
  // TODO: Safe alternative

  /**
   * Executes this SQL as an update statement.
   * @return Count of updated row(s)
   */
  @throws[java.sql.SQLException]("If statement is query not update")
  def executeUpdate()(implicit connection: Connection): Int =
    preparedStatement(connection).acquireAndGet(_.executeUpdate())
  //TODO: Safe alternative

  /**
   * Executes this SQL as an insert statement.
   *
   * @param generatedKeysParser Parser for generated key (default: scalar long)
   * @return Parsed generated keys
   *
   * {{{
   * import anorm.SqlParser.scalar
   *
   * val keys1 = SQL("INSERT INTO Test(x) VALUES ({x})").
   *   on("x" -> "y").executeInsert()
   *
   * val keys2 = SQL("INSERT INTO Test(x) VALUES ({x})").
   *   on("x" -> "y").executeInsert(scalar[String].singleOpt)
   * // ... generated string key
   * }}}
   */
  def executeInsert[A](generatedKeysParser: ResultSetParser[A] = SqlParser.scalar[Long].singleOpt)(implicit connection: Connection): A =
    Sql.asTry(generatedKeysParser, preparedStatement(connection, true).
      flatMap { stmt =>
        stmt.executeUpdate()
        managed(stmt.getGeneratedKeys)
      }, resultSetOnFirstRow).get // TODO: Safe alternative

  /**
   * Executes this SQL query, and returns its result.
   *
   * {{{
   * implicit val conn: Connection = openConnection
   * val res: SqlQueryResult =
   *   SQL("SELECT text_col FROM table WHERE id = {code}").
   *   on("code" -> code).executeQuery()
   * // Check execution context; e.g. res.statementWarning
   * val str = res as scalar[String].single // going with row parsing
   * }}}
   */
  def executeQuery()(implicit connection: Connection): SqlQueryResult =
    SqlQueryResult(resultSet(connection), resultSetOnFirstRow)

}

object Sql { // TODO: Rename to SQL
  import scala.util.{ Success => TrySuccess, Try }
  import scala.util.control.NoStackTrace

  private[anorm] def withResult[T](res: ManagedResource[ResultSet], onFirstRow: Boolean)(op: Option[Cursor] => T): ManagedResource[T] =
    res.map(rs => op(if (onFirstRow) Cursor.onFirstRow(rs) else Cursor(rs)))

  private[anorm] def asTry[T](parser: ResultSetParser[T], rs: ManagedResource[ResultSet], onFirstRow: Boolean)(implicit connection: Connection): Try[T] =
    Try(withResult(rs, onFirstRow)(parser) acquireAndGet identity).
      flatMap(_.fold[Try[T]](_.toFailure, TrySuccess.apply))

  @annotation.tailrec
  private[anorm] def zipParams(ns: Seq[String], vs: Seq[ParameterValue], ps: Map[String, ParameterValue]): Map[String, ParameterValue] = (ns.headOption, vs.headOption) match {
    case (Some(n), Some(v)) => zipParams(ns.tail, vs.tail, ps + (n -> v))
    case _ => ps
  }

  @inline
  private def toSql(ts: List[StatementToken], buf: StringBuilder): StringBuilder = ts.foldLeft(buf) {
    case (sql, StringToken(t)) => sql ++= t
    case (sql, PercentToken) => sql += '%'
    case (sql, _) => sql
  }

  private class MissingParameter(after: String) extends java.util.NoSuchElementException(s"Missing parameter value after: $after") with NoStackTrace {}

  private object NoMorePlaceholder extends Exception("No more placeholder")
    with NoStackTrace {}

  @annotation.tailrec
  def prepareQuery(tok: List[TokenGroup], ns: List[String], ps: Map[String, ParameterValue], i: Int, buf: StringBuilder, vs: List[(Int, ParameterValue)]): Try[(String, Seq[(Int, ParameterValue)])] =
    (tok.headOption, ns.headOption.flatMap(ps.lift(_))) match {
      case (Some(TokenGroup(pr, Some(pl))), Some(p)) => {
        val (frag, c): (String, Int) = p.toSql
        val prepared = toSql(pr, buf) ++= frag

        prepareQuery(tok.tail, ns.tail, ps, i + c, prepared, (i, p) :: vs)
      }
      case (Some(TokenGroup(pr, Some(pl))), _) =>
        Failure(new MissingParameter(pr mkString ""))

      case (Some(TokenGroup(pr, None)), _) =>
        prepareQuery(tok.tail, ns, ps, i, toSql(pr, buf), vs)

      case (_, Some(p)) => {
        val (frag, c): (String, Int) = p.toSql
        prepareQuery(tok, ns.tail, ps, i + c, buf, (i, p) :: vs)
      }
      case (None, _) | (_, None) => TrySuccess(buf.toString -> vs.reverse)
      case _ => Failure(NoMorePlaceholder)
    }
}

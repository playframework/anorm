/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package anorm

import java.sql.{ Connection, PreparedStatement, ResultSet }

import scala.util.{ Failure, Try }

import scala.reflect.ClassTag

import resource.{ managed, ManagedResource, Resource }

/**
 * Untyped value wrapper.
 *
 * {{{
 * import anorm._
 *
 * def foo(v: Any) = SQL("UPDATE t SET val = {o}").on('o -> anorm.Object(v))
 * }}}
 */
case class Object(value: Any)

/**
 * Wrapper to use a value sequence as SQL parameter, with custom formatting.
 *
 * {{{
 * import anorm._
 *
 * def foo(implicit con: java.sql.Connection) = {
 *   SQL("SELECT * FROM t WHERE {s}").
 *     on("s" -> SeqParameter(Seq("a", "b"), " OR ", "cat = "))
 *   // Will execute as:
 *   // SELECT * FROM t WHERE cat = 'a' OR cat = 'b'
 * }
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
  def apply[A](seq: Seq[A], sep: String = ", ", pre: String = "", post: String = ""): SeqParameter[A] =
    new SeqParameter[A] {
      val values    = seq
      val separator = sep
      val before    = Option(pre)
      val after     = Option(post)
    }
}

private[anorm] trait Sql extends WithResult {
  private implicit val statementCls: ClassTag[PreparedStatement] =
    statementClassTag

  private implicit val resultSetCls: ClassTag[ResultSet] = resultSetClassTag

  private[anorm] def unsafeStatement(connection: Connection, getGeneratedKeys: Boolean = false): PreparedStatement

  private[anorm] def unsafeStatement(
      connection: Connection,
      generatedColumn: String,
      generatedColumns: Seq[String]
  ): PreparedStatement

  protected final def preparedStatement(
      connection: Connection,
      getGeneratedKeys: Boolean = false
  ): ManagedResource[PreparedStatement] = {
    implicit val res: Resource[PreparedStatement] = StatementResource

    managed(unsafeStatement(connection, getGeneratedKeys))
  }

  final def preparedStatement(
      connection: Connection,
      generatedColumn: String,
      generatedColumns: Seq[String]
  ): ManagedResource[PreparedStatement] = {
    implicit val res: Resource[PreparedStatement] = StatementResource

    managed(unsafeStatement(connection, generatedColumn, generatedColumns))
  }

  /**
   * Executes this statement as query (see [[executeQuery]]) and returns result.
   */
  protected def resultSet(connection: Connection): ManagedResource[ResultSet] =
    preparedStatement(connection).flatMap { stmt =>
      implicit val res: Resource[ResultSet] = ResultSetResource

      managed(stmt.executeQuery())
    }

  private[anorm] def unsafeResultSet(connection: Connection): ResultSet =
    unsafeStatement(connection).executeQuery()

  /**
   * Executes this SQL statement.
   * @return true if resultset was returned from execution
   * (statement is query), or false if it executed update.
   *
   * {{{
   * import anorm._
   *
   * def res(implicit con: java.sql.Connection): Boolean =
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
  // TODO: Safe alternative

  /**
   * Executes this SQL as an insert statement.
   *
   * @param generatedKeysParser Parser for generated key (default: scalar long)
   * @return Parsed generated keys
   *
   * {{{
   * import anorm._, SqlParser.scalar
   *
   * def foo(implicit con: java.sql.Connection) = {
   *   val keys1 = SQL("INSERT INTO Test(x) VALUES ({x})").
   *     on("x" -> "y").executeInsert()
   *
   *   val keys2 = SQL("INSERT INTO Test(x) VALUES ({x})").
   *     on("x" -> "y").executeInsert(scalar[String].singleOpt)
   *   // ... generated string key
   * }
   * }}}
   */
  @SuppressWarnings(Array("TryGet" /* TODO: Make it safer */ ))
  def executeInsert[A](generatedKeysParser: ResultSetParser[A] = SqlParser.scalar[Long].singleOpt)(implicit
      connection: Connection
  ): A = execInsert[A](preparedStatement(_, true), generatedKeysParser, ColumnAliaser.empty).get

  /**
   * Executes this SQL as an insert statement.
   *
   * @param generatedColumn the first (mandatory) column name to consider from the generated keys
   * @param otherColumns the other (possibly none) column name(s) from the generated keys
   * @param generatedKeysParser the parser for generated key (default: scalar long)
   * @return Parsed generated keys
   *
   * {{{
   * import anorm._, SqlParser.scalar
   *
   * def foo(implicit con: java.sql.Connection) = {
   *   val keys1 = SQL("INSERT INTO Test(x) VALUES ({x})").
   *     on("x" -> "y").executeInsert1("generatedCol", "colB")()
   *
   *   val keys2 = SQL("INSERT INTO Test(x) VALUES ({x})").
   *     on("x" -> "y").executeInsert1("generatedCol")(scalar[String].singleOpt)
   *   // ... generated string key
   * }
   * }}}
   */
  def executeInsert1[A](generatedColumn: String, otherColumns: String*)(
      generatedKeysParser: ResultSetParser[A] = SqlParser.scalar[Long].singleOpt
  )(implicit connection: Connection): Try[A] =
    execInsert[A](preparedStatement(_, generatedColumn, otherColumns), generatedKeysParser, ColumnAliaser.empty)

  /**
   * Executes this SQL as an insert statement.
   *
   * @param generatedColumn the first (mandatory) column name to consider from the generated keys
   * @param otherColumns the other (possibly none) column name(s) from the generated keys
   * @param generatedKeysParser the parser for generated key (default: scalar long)
   * @param aliaser the column aliaser
   * @return Parsed generated keys
   *
   * {{{
   * import anorm._, SqlParser.scalar
   *
   * def foo(implicit con: java.sql.Connection) = {
   *   val keys1 = SQL("INSERT INTO Test(x) VALUES ({x})").
   *     on("x" -> "y").executeInsert1("generatedCol", "colB")()
   *
   *   val keys2 = SQL("INSERT INTO Test(x) VALUES ({x})").
   *     on("x" -> "y").executeInsert1("generatedCol")(scalar[String].singleOpt)
   *   // ... generated string key
   * }
   * }}}
   */
  def executeInsert2[A](generatedColumn: String, otherColumns: String*)(
      generatedKeysParser: ResultSetParser[A] = SqlParser.scalar[Long].singleOpt,
      aliaser: ColumnAliaser
  )(implicit connection: Connection): Try[A] =
    execInsert[A](preparedStatement(_, generatedColumn, otherColumns), generatedKeysParser, aliaser)

  private def execInsert[A](
      prep: Connection => ManagedResource[PreparedStatement],
      generatedKeysParser: ResultSetParser[A],
      as: ColumnAliaser
  )(implicit connection: Connection): Try[A] = {
    @com.github.ghik.silencer.silent
    implicit def cls: ClassTag[ResultSet] = resultSetClassTag

    Sql.asTry(
      generatedKeysParser,
      prep(connection).flatMap { stmt =>
        stmt.executeUpdate()
        managed(stmt.getGeneratedKeys)
      },
      resultSetOnFirstRow,
      as
    )
  }

  /**
   * Executes this SQL query, and returns its result.
   *
   * {{{
   * import anorm._, SqlParser.scalar
   *
   * def foo(code: String)(implicit con: java.sql.Connection) = {
   *   val res: SqlQueryResult =
   *     SQL("SELECT text_col FROM table WHERE id = {code}").
   *     on("code" -> code).executeQuery()
   *   // Check execution context; e.g. res.statementWarning
   *   val str = res as scalar[String].single // going with row parsing
   * }
   * }}}
   */
  def executeQuery()(implicit connection: Connection): SqlQueryResult =
    SqlQueryResult(resultSet(connection), resultSetOnFirstRow)

}

object Sql { // TODO: Rename to SQL
  import scala.util.{ Success => TrySuccess, Try }
  import scala.util.control.NoStackTrace

  private[anorm] def unsafeCursor(res: ResultSet, onFirstRow: Boolean, as: ColumnAliaser): Option[Cursor] = {
    if (onFirstRow) Cursor.onFirstRow(res, as) else Cursor(res, as)
  }

  private[anorm] def withResult[T](res: ManagedResource[ResultSet], onFirstRow: Boolean, as: ColumnAliaser)(
      op: Option[Cursor] => T
  ): ManagedResource[T] =
    res.map(rs => op(unsafeCursor(rs, onFirstRow, as)))

  private[anorm] def asTry[T](
      parser: ResultSetParser[T],
      rs: ManagedResource[ResultSet],
      onFirstRow: Boolean,
      as: ColumnAliaser
  ): Try[T] = Try(withResult(rs, onFirstRow, as)(parser).acquireAndGet(identity))
    .flatMap(_.fold[Try[T]](_.toFailure, TrySuccess.apply))

  @annotation.tailrec
  private[anorm] def zipParams(
      ns: Seq[String],
      vs: Seq[ParameterValue],
      ps: Map[String, ParameterValue]
  ): Map[String, ParameterValue] = (ns.headOption, vs.headOption) match {
    case (Some(n), Some(v)) => zipParams(ns.tail, vs.tail, ps + (n -> v))
    case _                  => ps
  }

  @inline
  private def toSql(ts: List[StatementToken], buf: StringBuilder): StringBuilder = ts.foldLeft(buf) {
    case (sql, StringToken(t)) => sql ++= t
    case (sql, PercentToken)   => sql += '%'
    // TODO: Remove; case (sql, _)              => sql
  }

  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final class MissingParameter(after: String, placeholder: String)
      extends java.util.NoSuchElementException(s"Missing parameter value for '$placeholder' after: $after")
      with NoStackTrace {
    @deprecated("Create this exception while supplying the missing placeholder value", "2.6.1")
    def this(after: String) = this(after, "<unknown>")
  }

  object NoMorePlaceholder extends Exception("No more placeholder") with NoStackTrace {}

  @deprecated("Internal function: will be made private", "2.5.2")
  def prepareQuery(
      tok: List[TokenGroup],
      ns: List[String],
      ps: Map[String, ParameterValue],
      i: Int,
      buf: StringBuilder,
      vs: List[(Int, ParameterValue)]
  ): Try[(String, Seq[(Int, ParameterValue)])] = query(tok, ns, ps, i, buf, vs)

  @annotation.tailrec
  private[anorm] def query(
      tok: Seq[TokenGroup],
      ns: List[String],
      ps: Map[String, ParameterValue],
      i: Int,
      buf: StringBuilder,
      vs: List[(Int, ParameterValue)]
  ): Try[(String, Seq[(Int, ParameterValue)])] =
    (tok.headOption, ns.headOption.flatMap(ps.lift(_))) match {
      case (Some(TokenGroup(pr, Some(_))), Some(p)) => {
        val (frag, c): (String, Int) = p.toSql
        val prepared                 = toSql(pr, buf) ++= frag

        query(tok.tail, ns.tail, ps, i + c, prepared, (i, p) :: vs)
      }

      case (Some(TokenGroup(pr, Some(placeholder))), _) =>
        Failure(new MissingParameter(pr.mkString(", "), placeholder))

      case (Some(TokenGroup(pr, None)), _) =>
        query(tok.tail, ns, ps, i, toSql(pr, buf), vs)

      case (_, Some(p)) => {
        val c: Int = p.toSql._2
        query(tok, ns.tail, ps, i + c, buf, (i, p) :: vs)
      }

      case (None, _) | (_, None) => TrySuccess(buf.toString -> vs.reverse)
      case _                     => Failure(NoMorePlaceholder)
    }
}

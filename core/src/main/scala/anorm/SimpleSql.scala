package anorm

import java.sql.{ Connection, PreparedStatement }

/** Simple/plain SQL. */
case class SimpleSql[T](
    sql: SqlQuery,
    params: Map[String, ParameterValue],
    defaultParser: RowParser[T],
    resultSetOnFirstRow: Boolean = false
) extends Sql {

  /**
   * Returns the query prepared with named parameters.
   *
   * {{{
   * import anorm._
   *
   * val baseSql = SQL("SELECT * FROM table WHERE id = {id}") // one named param
   * val preparedSql = baseSql.on("id" -> "value")
   * }}}
   */
  def on(args: NamedParameter*): SimpleSql[T] =
    copy(params = this.params ++ args.map(_.tupled))

  /**
   * Returns the query prepared with parameters using initial order
   * of placeholder in statement.
   *
   * {{{
   * import anorm._
   *
   * val baseSql =
   *   SQL("SELECT * FROM table WHERE name = {name} AND lang = {lang}")
   *
   * val preparedSql = baseSql.onParams("1st", "2nd")
   * // 1st param = name, 2nd param = lang
   * }}}
   */
  def onParams(args: ParameterValue*): SimpleSql[T] =
    copy(params = this.params ++ Sql.zipParams(sql.paramsInitialOrder, args, Map.empty))

  /**
   * Returns the query prepared with the named parameters,
   * provided by the appropriate `converter`.
   *
   * @param value the value to be converted as list of [[NamedParameter]]
   * @param converter the function used to convert the `value`
   * @tparam U the type of the value
   */
  def bind[U](value: U)(implicit converter: ToParameterList[U]): SimpleSql[T] =
    on(converter(value): _*)

  private val prepareNoGeneratedKeys = { (con: Connection, sql: String) =>
    con.prepareStatement(sql)
  }

  private val prepareGeneratedKeys = { (con: Connection, sql: String) =>
    con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
  }

  private def prepareGeneratedCols(columns: Array[String]) = { (con: Connection, sql: String) =>
    con.prepareStatement(sql, columns)
  }

  def unsafeStatement(connection: Connection, getGeneratedKeys: Boolean = false) = {
    if (getGeneratedKeys) unsafeStatement(connection, prepareGeneratedKeys)
    else unsafeStatement(connection, prepareNoGeneratedKeys)
  }

  def unsafeStatement(connection: Connection, generatedColumn: String, generatedColumns: Seq[String]) =
    unsafeStatement(connection, prepareGeneratedCols((generatedColumn +: generatedColumns).toArray))

  private def unsafeStatement(
      connection: Connection,
      prep: (Connection, String) => PreparedStatement
  ): PreparedStatement = {
    @SuppressWarnings(Array("TryGet"))
    def unsafe = Sql
      .query(sql.stmt.tokens, sql.paramsInitialOrder, params, 0, new StringBuilder(), List.empty[(Int, ParameterValue)])
      .get

    val (psql, vs): (String, Seq[(Int, ParameterValue)]) = unsafe

    val stmt = prep(connection, psql)

    sql.fetchSize.foreach(stmt.setFetchSize(_))
    sql.timeout.foreach(stmt.setQueryTimeout(_))

    vs.foreach { case (i, v) => v.set(stmt, i + 1) }

    stmt
  }

  /** Prepares query with given row parser. */
  @deprecated(message = "Use [[as]]", since = "2.5.1")
  def using[U](p: RowParser[U]): SimpleSql[U] = copy(sql, params, p)

  def map[A](f: T => A): SimpleSql[A] =
    copy(defaultParser = defaultParser.map(f))

  /** Returns a copy with updated timeout. */
  def withQueryTimeout(seconds: Option[Int]): SimpleSql[T] =
    copy(sql = sql.withQueryTimeout(seconds))

  /** Returns a copy with updated flag. */
  def withResultSetOnFirstRow(onFirst: Boolean): SimpleSql[T] =
    copy(resultSetOnFirstRow = onFirst)

  /** Fetch size */
  def fetchSize: Option[Int] = sql.fetchSize

  /**
   * Returns this query with the fetch suze updated to the row `count`.
   * @see [[SqlQuery.fetchSize]]
   */
  def withFetchSize(count: Option[Int]): SimpleSql[T] =
    copy(sql.withFetchSize(count))

}

object SimpleSql {
  final class SimpleSqlShow[T](sql: SimpleSql[T]) extends Show {

    def show = s"SimpleSql(${Show.mkString(sql.sql)})"
  }

  final class ShowMaker[T] extends Show.Maker[SimpleSql[T]] {
    def apply(subject: SimpleSql[T]): Show = new SimpleSqlShow(subject)
  }

  implicit def showMaker[T]: Show.Maker[SimpleSql[T]] = new ShowMaker[T]
}

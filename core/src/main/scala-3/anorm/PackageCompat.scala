package anorm

import java.sql.Connection

import scala.util.Try

private[anorm] trait PackageCompat:

  extension (query: SqlQuery)
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
    def on(args: NamedParameter*): SimpleSql[Row] =
      query.asSimple.on(args: _*)

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
    def onParams(args: ParameterValue*): SimpleSql[Row] = {
      val simple = query.asSimple

      simple.copy(params = simple.params ++ Sql.zipParams(simple.sql.paramsInitialOrder, args, Map.empty))
    }

    /**
     * Returns the query prepared with the named parameters,
     * provided by the appropriate `converter`.
     *
     * @param value the value to be converted as list of [[NamedParameter]]
     * @param converter the function used to convert the `value`
     * @tparam U the type of the value
     */
    def bind[U](value: U)(using converter: ToParameterList[U]): SimpleSql[Row] =
      query.asSimple.bind[U](value)

    def map[T](f: Row => T): SimpleSql[T] = {
      val simple = query.asSimple

      simple.copy(defaultParser = simple.defaultParser.map(f))
    }

    /** Returns a copy with updated timeout. */
    def withQueryTimeout(seconds: Option[Int]): SimpleSql[Row] = {
      val simple = query.asSimple

      simple.copy(sql = simple.sql.withQueryTimeout(seconds))
    }

    /** Returns a copy with updated flag. */
    def withResultSetOnFirstRow(onFirst: Boolean): SimpleSql[Row] =
      query.asSimple.copy(resultSetOnFirstRow = onFirst)

    /** Fetch size */
    def fetchSize: Option[Int] = None

    /**
     * Returns this query with the fetch suze updated to the row `count`.
     * @see [[SqlQuery.fetchSize]]
     */
    def withFetchSize(count: Option[Int]): SimpleSql[Row] = {
      val simple = query.asSimple

      simple.copy(simple.sql.withFetchSize(count))
    }

    /**
     * Aggregates over all rows using the specified operator.
     *
     * @param z the start value
     * @param aliaser the column aliaser
     * @param op Aggregate operator
     * @return Either list of failures at left, or aggregated value
     * @see #foldWhile
     * @see #withResult
     */
    def fold[T](z: => T, aliaser: ColumnAliaser)(
        op: (T, Row) => T
    )(using connection: Connection): Either[List[Throwable], T] =
      query.asSimple.fold[T](z, aliaser)(op)(connection)

    /**
     * Aggregates over part of or the while row stream,
     * using the specified operator.
     *
     * @param z the start value
     * @param aliaser the column aliaser
     * @param op Aggregate operator. Returns aggregated value along with true if aggregation must process next value, or false to stop with current value.
     * @return Either list of failures at left, or aggregated value
     * @see #withResult
     */
    def foldWhile[T](z: => T, aliaser: ColumnAliaser)(
        op: (T, Row) => (T, Boolean)
    )(using connection: Connection): Either[List[Throwable], T] =
      query.asSimple.foldWhile[T](z, aliaser)(op)(connection)

    /**
     * Processes all or some rows from current result.
     *
     * @param op Operation applied with row cursor
     * @param aliaser the column aliaser
     *
     * {{{
     * import java.sql.Connection
     * import anorm._
     *
     * @annotation.tailrec
     * def go(c: Option[Cursor], l: List[Row]): List[Row] = c match {
     *   case Some(cursor) => go(cursor.next, l :+ cursor.row)
     *   case _ => l
     * }
     *
     * def l(using con: Connection): Either[List[Throwable], List[Row]] =
     *   SQL"SELECT * FROM Test".withResult(go(_, List.empty))
     * }}}
     */
    def withResult[T](op: Option[Cursor] => T)(using connection: Connection): Either[List[Throwable], T] =
      withResult[T](op, ColumnAliaser.empty)

    /**
     * Processes all or some rows from current result.
     *
     * @param op Operation applied with row cursor
     *
     * {{{
     * import java.sql.Connection
     * import anorm._
     *
     * @annotation.tailrec
     * def go(c: Option[Cursor], l: List[Row]): List[Row] = c match {
     *   case Some(cursor) => go(cursor.next, l :+ cursor.row)
     *   case _ => l
     * }
     *
     * def l(implicit con: Connection): Either[List[Throwable], List[Row]] =
     *   SQL"SELECT * FROM Test".withResult(go(_, List.empty))
     * }}}
     */
    def withResult[T](op: Option[Cursor] => T, aliaser: ColumnAliaser)(implicit
        connection: Connection
    ): Either[List[Throwable], T] =
      query.asSimple.withResult[T](op, aliaser)(connection)

    /**
     * Converts this query result as `T`, using parser.
     *
     * @param parser the result parser
     * @see #asTry
     */
    def as[T](parser: ResultSetParser[T])(implicit connection: Connection): T =
      query.asSimple.asTry[T](parser, ColumnAliaser.empty).get

    /**
     * Converts this query result as `T`, using parser.
     *
     * @param parser the result parser
     * @param aliaser the column aliaser
     */
    def asTry[T](parser: ResultSetParser[T], aliaser: ColumnAliaser = ColumnAliaser.empty)(implicit
        connection: Connection
    ): Try[T] = query.asSimple.asTry[T](parser, aliaser)(connection)

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
    def execute()(using connection: Connection): Boolean =
      query.asSimple.execute()

    /**
     * Executes this SQL as an update statement.
     * @return Count of updated row(s)
     */
    @throws[java.sql.SQLException]("If statement is query not update")
    def executeUpdate()(using connection: Connection): Int =
      query.asSimple.executeUpdate()

end PackageCompat

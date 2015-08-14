package anorm

import java.sql.Connection

/** Simple/plain SQL. */
case class SimpleSql[T](sql: SqlQuery, params: Map[String, ParameterValue], defaultParser: RowParser[T], resultSetOnFirstRow: Boolean = false) extends Sql {

  /**
   * Returns query prepared with named parameters.
   *
   * {{{
   * import anorm.toParameterValue
   *
   * val baseSql = SQL("SELECT * FROM table WHERE id = {id}") // one named param
   * val preparedSql = baseSql.withParams("id" -> "value")
   * }}}
   */
  def on(args: NamedParameter*): SimpleSql[T] =
    copy(params = this.params ++ args.map(_.tupled))

  /**
   * Returns query prepared with parameters using initial order
   * of placeholder in statement.
   *
   * {{{
   * import anorm.toParameterValue
   *
   * val baseSql =
   *   SQL("SELECT * FROM table WHERE name = {name} AND lang = {lang}")
   *
   * val preparedSql = baseSql.onParams("1st", "2nd")
   * // 1st param = name, 2nd param = lang
   * }}}
   */
  def onParams(args: ParameterValue*): SimpleSql[T] =
    copy(params = this.params ++ Sql.zipParams(
      sql.paramsInitialOrder, args, Map.empty))

  def preparedStatement(connection: Connection, getGeneratedKeys: Boolean = false) = {
    implicit val res = StatementResource
    resource.managed {
      val (psql, vs): (String, Seq[(Int, ParameterValue)]) = Sql.prepareQuery(sql.stmt.tokens, sql.paramsInitialOrder, params, 0, new StringBuilder(), List.empty[(Int, ParameterValue)]).get

      val stmt = if (getGeneratedKeys) connection.prepareStatement(psql, java.sql.Statement.RETURN_GENERATED_KEYS) else connection.prepareStatement(psql)

      sql.timeout.foreach(stmt.setQueryTimeout(_))

      vs foreach { case (i, v) => v.set(stmt, i + 1) }

      stmt
    }
  }

  /**
   * Prepares query with given row parser.
   *
   * {{{
   * import anorm.{ SQL, SqlParser }
   *
   * val res: Int = SQL("SELECT 1").using(SqlParser.scalar[Int]).single
   * // Equivalent to: SQL("SELECT 1").as(SqlParser.scalar[Int].single)
   * }}}
   */
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
}

package anorm

import java.sql.Connection

/** Simple/plain SQL. */
case class SimpleSql[T](sql: SqlQuery, params: Map[String, ParameterValue], defaultParser: RowParser[T]) extends Sql {

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

  /** Applies current parser with optionnal list of rows (0..n). */
  @deprecated(
    message = """Use `SQL("...").as(parser.*)`""", since = "2.3.5")
  def list()(implicit connection: Connection): List[T] = as(defaultParser.*)

  /** Applies current parser to exactly on row. */
  @deprecated(
    message = """Use `SQL("...").as(parser.single)`""", since = "2.3.5")
  def single()(implicit connection: Connection): T = as(defaultParser.single)

  /** Applies current parser to one optional row. */
  @deprecated(
    message = """Use `SQL("...").as(parser.singleOpt)`""", since = "2.3.5")
  def singleOpt()(implicit connection: Connection): Option[T] =
    as(defaultParser.singleOpt)

  @deprecated(message = "Use [[preparedStatement]]", since = "2.3.6")
  def getFilledStatement(connection: Connection, getGeneratedKeys: Boolean = false) = {
    val st: (TokenizedStatement, Seq[(Int, ParameterValue)]) = Sql.prepareQuery(
      sql.stmt, 0, sql.paramsInitialOrder.map(params), Nil)

    val psql = TokenizedStatement.toSql(st._1).get // TODO: Make it safe
    val stmt = if (getGeneratedKeys) connection.prepareStatement(psql, java.sql.Statement.RETURN_GENERATED_KEYS) else connection.prepareStatement(psql)

    sql.timeout.foreach(stmt.setQueryTimeout(_))

    st._2 foreach { p =>
      val (i, v) = p
      v.set(stmt, i + 1)
    }

    stmt
  }

  def preparedStatement(connection: Connection, getGeneratedKeys: Boolean = false) = resource.managed(getFilledStatement(connection, getGeneratedKeys))

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
  def using[U](p: RowParser[U]): SimpleSql[U] = copy(sql, params, p)
  // Deprecates with .as ?

  def map[A](f: T => A): SimpleSql[A] =
    copy(defaultParser = defaultParser.map(f))

  def withQueryTimeout(seconds: Option[Int]): SimpleSql[T] =
    copy(sql = sql.withQueryTimeout(seconds))

}

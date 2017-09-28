package anorm

import java.sql.{ Connection, PreparedStatement }

private[anorm] object BatchSqlErrors {
  val HeterogeneousParameterMaps = "if each map hasn't same parameter names"
  val ParameterNamesNotMatchingPlaceholders =
    "if parameter names don't match query placeholders"
  val UnexpectedParameterName = "if `args` contains unexpected parameter name"
  val MissingParameter = "if `args` is missing some expected parameter names"
}

/** SQL batch */
sealed trait BatchSql {
  /** SQL query */
  def sql: SqlQuery

  /** Names of parameter expected for each parameter map */
  def names: Set[String]

  /** Named parameters */
  def params: Seq[Map[String, ParameterValue]] // checked: maps have same keys

  /**
   * Adds a parameter map, created by zipping values with query placeholders
   * ([[SqlQuery.paramsInitialOrder]]). If parameter is used for more than one
   * placeholder, it will result in a parameter map with smaller size than
   * given arguments (as duplicate entry are removed from map).
   */
  @throws[IllegalArgumentException](BatchSqlErrors.MissingParameter)
  @throws[IllegalArgumentException](BatchSqlErrors.ParameterNamesNotMatchingPlaceholders)
  def addBatchParams(args: ParameterValue*): BatchSql = {
    if (params.isEmpty) {
      BatchSql.Checked(
        sql,
        Seq(Sql.zipParams(sql.paramsInitialOrder, args, Map.empty)))
    } else {
      val m = checkedMap(sql.paramsInitialOrder.zip(args).
        foldLeft(Seq.empty[NamedParameter])((ps, t) =>
          ps :+ implicitly[NamedParameter](t)))

      copy(params = this.params :+ m)
    }
  }

  /**
   * Adds a parameter maps, created by zipping values with query placeholders
   * ([[SqlQuery.paramsInitialOrder]]). If parameter is used for more than one
   * placeholder, it will result in parameter maps with smaller size than
   * given arguments (as duplicate entry are removed from map).
   */
  @throws[IllegalArgumentException](BatchSqlErrors.MissingParameter)
  @throws[IllegalArgumentException](BatchSqlErrors.ParameterNamesNotMatchingPlaceholders)
  def addBatchParamsList(args: Traversable[Seq[ParameterValue]]): BatchSql = {
    if (params.isEmpty) {
      BatchSql.Checked(
        sql,
        args.map(Sql.zipParams(sql.paramsInitialOrder, _, Map.empty)))

    } else {
      val ms = args.map(x => checkedMap(sql.paramsInitialOrder.zip(x).
        foldLeft(Seq.empty[NamedParameter])((ps, t) =>
          ps :+ implicitly[NamedParameter](t))))

      copy(params = this.params ++ ms)
    }
  }

  /**
   * Adds arguments of type `T` that can be converted as query parameters.
   */
  @throws[IllegalArgumentException](BatchSqlErrors.MissingParameter)
  @throws[IllegalArgumentException](BatchSqlErrors.ParameterNamesNotMatchingPlaceholders)
  def bind[T](args: T*)(implicit converter: ToParameterList[T]): BatchSql = addBatchParamsList(args.map { converter(_).map(_.value) })

  @SuppressWarnings(Array("NullParameter"))
  def getFilledStatement(connection: Connection, getGeneratedKeys: Boolean = false) = fill(connection, null, getGeneratedKeys, params)

  def execute()(implicit connection: Connection): Array[Int] =
    getFilledStatement(connection).executeBatch()

  /** Add batch parameters to given statement. */
  private def addBatchParams(stmt: PreparedStatement, ps: Seq[(Int, ParameterValue)]): PreparedStatement = {
    ps foreach { case (i, v) => v.set(stmt, i + 1) }
    stmt.addBatch()
    stmt
  }

  @annotation.tailrec
  @SuppressWarnings(Array("NullParameter"))
  private def fill(con: Connection, statement: PreparedStatement, getGeneratedKeys: Boolean, pm: Seq[Map[String, ParameterValue]]): PreparedStatement = {
    @SuppressWarnings(Array("TryGet"))
    def unsafe(ps: Map[String, ParameterValue]) =
      Sql.query(sql.stmt.tokens, sql.paramsInitialOrder, ps,
        0, new StringBuilder(), List.empty[(Int, ParameterValue)]).get

    (statement, pm.headOption) match {
      case (null, Some(ps)) => { // First with parameters
        val (psql, vs): (String, Seq[(Int, ParameterValue)]) = unsafe(ps)

        val stmt = if (getGeneratedKeys) con.prepareStatement(psql, java.sql.Statement.RETURN_GENERATED_KEYS) else con.prepareStatement(psql)

        sql.fetchSize.foreach(stmt.setFetchSize(_))
        sql.timeout.foreach(stmt.setQueryTimeout(_))

        fill(con, addBatchParams(stmt, vs), getGeneratedKeys, pm.tail)
      }

      case (null, _ /*None*/ ) => { // First with no parameter
        val (psql, _): (String, Seq[(Int, ParameterValue)]) = unsafe(Map.empty)

        val stmt = if (getGeneratedKeys) con.prepareStatement(psql, java.sql.Statement.RETURN_GENERATED_KEYS) else con.prepareStatement(psql)

        sql.timeout.foreach(stmt.setQueryTimeout(_))

        stmt
      }

      case (stmt, Some(ps)) => {
        val (_, vs) = unsafe(ps)

        fill(con, addBatchParams(stmt, vs), getGeneratedKeys, pm.tail)
      }
      case _ => statement
    }
  }

  @throws[IllegalArgumentException](BatchSqlErrors.UnexpectedParameterName)
  @throws[IllegalArgumentException](BatchSqlErrors.MissingParameter)
  @inline private def checkedMap(args: Seq[NamedParameter]): Map[String, ParameterValue] = {
    val ps = args.foldLeft(Map[String, ParameterValue]()) { (m, np) =>
      if (!names.contains(np.name)) throw new IllegalArgumentException(s"""Unexpected parameter name: ${np.name} != expected ${names mkString ", "}""")
      else m + np.tupled
    }

    if (ps.size != names.size) throw new IllegalArgumentException(s"""Missing parameters: ${names.filterNot(ps.contains(_)) mkString ", "}""")

    ps
  }

  /**
   * Returns this query with the timeout updated to `seconds` delay.
   * @see [[SqlQuery.timeout]]
   */
  def withQueryTimeout(seconds: Option[Int]): BatchSql =
    copy(sql.withQueryTimeout(seconds))

  /**
   * Returns this query with the fetch suze updated to the row `count`.
   * @see [[SqlQuery.fetchSize]]
   */
  def withFetchSize(count: Option[Int]): BatchSql =
    copy(sql.withFetchSize(count))

  private def copy(sql: SqlQuery = this.sql, names: Set[String] = this.names, params: Seq[Map[String, ParameterValue]] = this.params) = BatchSql.Copy(sql, names, params)
}

/** SQL batch companion */
object BatchSql {
  /**
   * Creates a batch from given `sql` statement,
   * with a `first` parameter list for for SQL execution, and zero or many
   * `other` parameter lists for other SQL executions, after in the same batch.
   *
   * @param sql SQL statement
   * @param first Parameter list for the first SQL execution
   * @param other Parameter lists for other SQL executions in the same batch
   *
   * {{{
   * import anorm.{ BatchSql, NamedParameter }
   *
   * BatchSql("EXEC proc ?, ?", // Batch with 1 execution
   *   Seq[NamedParameter]("a" -> "Foo", "b" -> "Bar"))
   *
   * BatchSql("EXEC proc ?, ?", // Batch with 2 executions
   *   Seq[NamedParameter]("a" -> "Foo", "b" -> "Bar"),
   *   Seq[NamedParameter]("a" -> "Lorem", "b" -> "Ipsum"))
   * }}}
   */
  @throws[IllegalArgumentException](BatchSqlErrors.HeterogeneousParameterMaps)
  @throws[IllegalArgumentException](BatchSqlErrors.ParameterNamesNotMatchingPlaceholders)
  def apply(sql: String, first: Seq[NamedParameter], other: Seq[NamedParameter]*): BatchSql = Checked(SQL(sql), (Seq(first) ++: other).map(_.map(_.tupled).toMap))

  @throws[IllegalArgumentException](BatchSqlErrors.HeterogeneousParameterMaps)
  @throws[IllegalArgumentException](BatchSqlErrors.ParameterNamesNotMatchingPlaceholders)
  @SuppressWarnings(Array("MethodNames"))
  private[anorm] def Checked[M](query: SqlQuery, ps: Traversable[Map[String, ParameterValue]]): BatchSql = ps.headOption.
    fold(Copy(query, Set.empty, Nil)) { m =>
      val ks = m.keySet

      if (!matchPlaceholders(query, ks))
        throw new IllegalArgumentException(s"""Expected parameter names don't correspond to placeholders in query: ${ks mkString ", "} not matching ${query.paramsInitialOrder mkString ", "}""")

      paramNames(ps.tail, m.keySet) match {
        case Left(err) => throw new IllegalArgumentException(err)
        case Right(ns) => Copy(query, ns, ps.toSeq)
      }
    }

  /**
   * Checks whether parameter `names` matches [[SqlQuery.paramsInitialOrder]].
   */
  @inline private[anorm] def matchPlaceholders(query: SqlQuery, names: Set[String]): Boolean = {
    val pl = query.paramsInitialOrder.toSet
    (pl.size == names.size && pl.intersect(names).size == names.size)
  }

  /** Get parameter names */
  @annotation.tailrec
  private def paramNames(ps: Traversable[Map[String, ParameterValue]], ns: Set[String]): Either[String, Set[String]] = ps.headOption match {
    case Some(m) =>
      if (ns.intersect(m.keySet).size != m.size)
        Left(s"""Unexpected parameter names: ${m.keySet mkString ", "} != expected ${ns mkString ", "}""")
      else paramNames(ps.tail, ns)
    case _ => Right(ns)
  }

  private[anorm] case class Copy(sql: SqlQuery, names: Set[String], params: Seq[Map[String, ParameterValue]]) extends BatchSql
}

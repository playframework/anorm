package anorm

import java.sql.{ Connection, PreparedStatement }
import resource.ManagedResource

/** Initial SQL query, without parameter values. */
sealed trait SqlQuery {
  private[anorm] def stmt: TokenizedStatement

  /** SQL statement */
  @deprecated(message = "Will be made private", since = "2.3.8")
  final def statement: String = stmt.tokens match {
    case TokenGroup(_, Some(pl)) :: _ =>
      throw new IllegalStateException(s"Placeholder not prepared: $pl")

    case TokenGroup(pr, None) :: Nil => pr.foldLeft(new StringBuilder()) {
      case (sql, StringToken(t)) => sql ++= t
      case (sql, PercentToken) => sql += '%'
      case (sql, _) => sql
    }.toString

    case _ => throw new IllegalStateException(s"Unexpected statement: $stmt")
  }

  /** Names of parameters in initial order */
  def paramsInitialOrder: List[String]

  /** Execution timeout */
  def timeout: Option[Int]

  /** Returns this query with the timeout updated to `seconds` delay. */
  def withQueryTimeout(seconds: Option[Int]): SqlQuery =
    SqlQuery.prepare(stmt, paramsInitialOrder, seconds, fetchSize)

  /** Fetch size */
  def fetchSize: Option[Int]

  /** Returns this query with the fetch suze updated to the row `count`. */
  def withFetchSize(count: Option[Int]): SqlQuery =
    SqlQuery.prepare(stmt, paramsInitialOrder, timeout, count)

  private[anorm] def asSimple: SimpleSql[Row] = asSimple(defaultParser)

  /**
   * Prepares query as a simple one.
   * @param parser Row parser
   *
   * {{{
   * import anorm.{ SQL, SqlParser }
   *
   * SQL("SELECT 1").asSimple(SqlParser.scalar[Int])
   * }}}
   */
  def asSimple[T](parser: RowParser[T] = defaultParser): SimpleSql[T] =
    SimpleSql(this, Map.empty, parser)

  private def defaultParser: RowParser[Row] = RowParser(Success(_))

  private[anorm] def copy(statement: TokenizedStatement = this.stmt, paramsInitialOrder: List[String] = this.paramsInitialOrder, timeout: Option[Int] = this.timeout, fetchSize: Option[Int] = this.fetchSize) = SqlQuery.prepare(statement, paramsInitialOrder, timeout, fetchSize)

  override def toString = s"SqlQuery($stmt, $paramsInitialOrder, timeout = $timeout, fetchSize = $fetchSize)"

}

/* TODO: Make it private[anorm] to prevent SqlQuery from being created with
 unchecked properties (e.g. unchecked/unparsed statement). */
object SqlQuery {

  /**
   * Returns prepared SQL query.
   *
   * @param st SQL statement (see [[SqlQuery.statement]])
   * @param params Parameter names in initial order (see [[SqlQuery.paramsInitialOrder]])
   * @param tmout Query execution timeout (see [[SqlQuery.timeout]])
   */
  private[anorm] def prepare(st: TokenizedStatement, params: List[String] = List.empty, tmout: Option[Int] = None, fetchSz: Option[Int] = None): SqlQuery = new SqlQuery {
    val stmt = st
    val paramsInitialOrder = params
    val timeout = tmout
    val fetchSize = fetchSz
  }

  /** Extractor for pattern matching */
  def unapply(query: SqlQuery): Option[(TokenizedStatement, List[String], Option[Int])] = Option(query).map(q => (q.stmt, q.paramsInitialOrder, q.timeout))

  final class SqlQueryShow(query: SqlQuery) extends Show {
    def show = s"SqlQuery(${Show.mkString(query.stmt)}, timeout = ${query.timeout}, fetchSize = ${query.fetchSize})"
  }

  implicit object ShowMaker extends Show.Maker[SqlQuery] {
    def apply(subject: SqlQuery): Show = new SqlQueryShow(subject)
  }
}

package anorm

import java.sql.{ Connection, SQLWarning }

import resource.ManagedResource

/**
 * A result from execution of an SQL query, row data and context
 * (e.g. statement warnings).
 *
 * @constructor create a result with a result set
 * @param resultSet Result set from executed query
 */
final case class SqlQueryResult(
    resultSet: ManagedResource[java.sql.ResultSet],
    resultSetOnFirstRow: Boolean = false
) extends WithResult {

  protected def resultSet(c: Connection) = resultSet

  /** Query statement already executed */
  val statement: ManagedResource[java.sql.Statement] =
    resultSet.map(_.getStatement)

  /**
   * Returns statement warning if there is some for this result.
   *
   * {{{
   * import anorm._, SqlParser.scalar
   *
   * def foo(paramVal: String)(implicit con: java.sql.Connection) = {
   *   val res = SQL("EXEC stored_proc {p}").on("p" -> paramVal).executeQuery()
   *
   *   res.statementWarning match {
   *     case Some(warning) =>
   *       warning.printStackTrace()
   *       None
   *
   *     case None =>
   *       // go on with row parsing ...
   *       res.as(scalar[String].singleOpt)
   *   }
   * }
   * }}}
   */
  def statementWarning: Option[SQLWarning] = {
    import resource.extractedEitherToEither

    statement.acquireFor(_.getWarnings).fold[Option[SQLWarning]](_.headOption.map(new SQLWarning(_)), Option(_))
  }
}

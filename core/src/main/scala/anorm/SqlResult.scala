package anorm

/** Parsed SQL result. */
sealed trait SqlResult[+A] { self =>
  // TODO: Review along with MayErr (unify?)

  def flatMap[B](k: A => SqlResult[B]): SqlResult[B] = self match {
    case Success(a)   => k(a)
    case e @ Error(_) => e
  }

  def map[B](f: A => B): SqlResult[B] = self match {
    case Success(a)   => Success(f(a))
    case e @ Error(_) => e
  }

  def collect[B](f: PartialFunction[A, B]): SqlResult[B] = self match {
    case Success(a) =>
      f.lift(a) match {
        case Some(b) =>
          Success(b)

        case None =>
          Error(SqlMappingError(s"Value ${a} is not matching"))
      }

    case Error(cause) =>
      Error(cause)
  }

  /**
   * Either applies function `e` if result is erroneous,
   * or function `f` with successful result if any.
   */
  def fold[B](e: SqlRequestError => B, f: A => B): B = self match {
    case Success(a) => f(a)
    case Error(err) => e(err)
  }
}

/** Successfully parsed result. */
case class Success[A](a: A) extends SqlResult[A]

/** Erroneous result (failure while parsing). */
case class Error(msg: SqlRequestError) extends SqlResult[Nothing]

private[anorm] trait WithResult {
  import java.sql.Connection
  import scala.util.Try

  /** ResultSet is initialized on first row (JDBC degraded) */
  def resultSetOnFirstRow: Boolean

  /** Returns underlying result set */
  protected def resultSet(connection: Connection): resource.ManagedResource[java.sql.ResultSet]

  /**
   * Aggregates over all rows using the specified operator.
   *
   * @param z the start value
   * @param op Aggregate operator
   * @return Either list of failures at left, or aggregated value
   * @see #foldWhile
   * @see #withResult
   */
  @deprecated(message = "Use `fold` with empty [[ColumnAliaser]]", since = "2.5.1")
  def fold[T](z: => T)(op: (T, Row) => T)(implicit connection: Connection): Either[List[Throwable], T] = {
    @annotation.tailrec
    def go(c: Option[Cursor], cur: T): T = c match {
      case Some(cursor) => go(cursor.next, op(cur, cursor.row))
      case _            => cur
    }

    withResult(go(_, z))
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
  )(implicit connection: Connection): Either[List[Throwable], T] = {
    @annotation.tailrec
    def go(c: Option[Cursor], cur: T): T = c match {
      case Some(cursor) => go(cursor.next, op(cur, cursor.row))
      case _            => cur
    }

    withResult(go(_, z), aliaser)
  }

  /**
   * Aggregates over part of or the while row stream,
   * using the specified operator.
   *
   * @param z the start value
   * @param op Aggregate operator. Returns aggregated value along with true if aggregation must process next value, or false to stop with current value.
   * @return Either list of failures at left, or aggregated value
   * @see #withResult
   */
  @deprecated(message = "Use `foldWhile` with empty [[ColumnAliaser]]", since = "2.5.1")
  def foldWhile[T](
      z: => T
  )(op: (T, Row) => (T, Boolean))(implicit connection: Connection): Either[List[Throwable], T] = {
    @annotation.tailrec
    def go(c: Option[Cursor], cur: T): T = c match {
      case Some(cursor) =>
        val (v, cont) = op(cur, cursor.row)
        if (!cont) v else go(cursor.next, v)
      case _ => cur
    }

    withResult(go(_, z))
  }

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
  )(implicit connection: Connection): Either[List[Throwable], T] = {
    @annotation.tailrec
    def go(c: Option[Cursor], cur: T): T = c match {
      case Some(cursor) =>
        val (v, cont) = op(cur, cursor.row)
        if (!cont) v else go(cursor.next, v)
      case _ => cur
    }

    withResult(go(_, z), aliaser)
  }

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
   * def l(implicit con: Connection): Either[List[Throwable], List[Row]] =
   *   SQL"SELECT * FROM Test".withResult(go(_, List.empty))
   * }}}
   */
  def withResult[T](op: Option[Cursor] => T)(implicit connection: Connection): Either[List[Throwable], T] =
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
  ): Either[List[Throwable], T] = {
    import resource.extractedEitherToEither

    Sql.withResult(resultSet(connection), resultSetOnFirstRow, aliaser)(op).acquireFor(identity)
  }

  /**
   * Converts this query result as `T`, using parser.
   *
   * @param parser the result parser
   * @see #asTry
   */
  @SuppressWarnings(Array("TryGet" /* TODO: Make it safer */ ))
  def as[T](parser: ResultSetParser[T])(implicit connection: Connection): T =
    asTry[T](parser, ColumnAliaser.empty).get

  /**
   * Converts this query result as `T`, using parser.
   *
   * @param parser the result parser
   * @param aliaser the column aliaser
   */
  def asTry[T](parser: ResultSetParser[T], aliaser: ColumnAliaser = ColumnAliaser.empty)(implicit
      connection: Connection
  ): Try[T] = Sql.asTry(parser, resultSet(connection), resultSetOnFirstRow, aliaser)

}

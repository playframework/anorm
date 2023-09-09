/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import java.sql.Connection

import scala.util.control.NonFatal

import scala.concurrent.{ Future, Promise }

import org.apache.pekko.stream.scaladsl.Source

/**
 * Anorm companion for the Pekko Streams.
 *
 * @define materialization It materializes a [[scala.concurrent.Future]] of [[scala.Int]] containing the number of rows read from the source upon completion, and a possible exception if row parsing failed.
 * @define sqlParam the SQL query
 * @define connectionParam the JDBC connection, which must not be closed until the source is materialized.
 * @define columnAliaserParam the column aliaser
 */
object PekkoStream {

  /**
   * Returns the rows parsed from the `sql` query as a reactive source.
   *
   * $materialization
   *
   * @tparam T the type of the result elements
   * @param sql $sqlParam
   * @param parser the result (row) parser
   * @param as $columnAliaserParam
   * @param connection $connectionParam
   *
   * {{{
   * import java.sql.Connection
   *
   * import scala.concurrent.Future
   *
   * import org.apache.pekko.stream.scaladsl.Source
   *
   * import anorm._
   *
   * def resultSource(implicit con: Connection): Source[String, Future[Int]] = PekkoStream.source(SQL"SELECT * FROM Test", SqlParser.scalar[String], ColumnAliaser.empty)
   * }}}
   */
  @SuppressWarnings(Array("UnusedMethodParameter"))
  def source[T](sql: => Sql, parser: RowParser[T], as: ColumnAliaser)(implicit
      con: Connection
  ): Source[T, Future[Int]] = Source.fromGraph(new ResultSource[T](con, sql, as, parser))

  /**
   * Returns the rows parsed from the `sql` query as a reactive source.
   *
   * $materialization
   *
   * @tparam T the type of the result elements
   * @param sql $sqlParam
   * @param parser the result (row) parser
   * @param connection $connectionParam
   */
  @SuppressWarnings(Array("UnusedMethodParameter"))
  def source[T](sql: => Sql, parser: RowParser[T])(implicit con: Connection): Source[T, Future[Int]] =
    source[T](sql, parser, ColumnAliaser.empty)

  /**
   * Returns the result rows from the `sql` query as an enumerator.
   * This is equivalent to `source[Row](sql, RowParser.successful, as)`.
   *
   * $materialization
   *
   * @param sql $sqlParam
   * @param as $columnAliaserParam
   * @param connection $connectionParam
   */
  def source(sql: => Sql, as: ColumnAliaser)(implicit connection: Connection): Source[Row, Future[Int]] =
    source(sql, RowParser.successful, as)

  /**
   * Returns the result rows from the `sql` query as an enumerator.
   * This is equivalent to
   * `source[Row](sql, RowParser.successful, ColumnAliaser.empty)`.
   *
   * $materialization
   *
   * @param sql $sqlParam
   * @param connection $connectionParam
   */
  def source(sql: => Sql)(implicit connnection: Connection): Source[Row, Future[Int]] =
    source(sql, RowParser.successful, ColumnAliaser.empty)

  // Internal stages

  import org.apache.pekko.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue, OutHandler }
  import org.apache.pekko.stream.{ Attributes, Outlet, SourceShape }

  import java.sql.ResultSet
  import scala.util.{ Failure, Success }

  private[anorm] class ResultSource[T](connection: Connection, sql: Sql, as: ColumnAliaser, parser: RowParser[T])
      extends GraphStageWithMaterializedValue[SourceShape[T], Future[Int]] {

    private[anorm] var resultSet: ResultSet = _

    override val toString     = "AnormQueryResult"
    val out: Outlet[T]        = Outlet(s"${toString}.out")
    val shape: SourceShape[T] = SourceShape(out)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
      val result = Promise[Int]()

      val logic = new GraphStageLogic(shape) with OutHandler {
        private var cursor: Option[Cursor] = None
        private var counter: Int           = 0

        private def failWith(cause: Throwable): Unit = {
          result.failure(cause)
          fail(out, cause)
          ()
        }

        override def preStart(): Unit = {
          try {
            resultSet = sql.unsafeResultSet(connection)
            nextCursor()
          } catch {
            case NonFatal(cause) => failWith(cause)
          }
        }

        override def postStop() = release()

        private def release(): Unit = {
          val stmt: Option[java.sql.Statement] = {
            if (resultSet != null && !resultSet.isClosed) {
              val s = resultSet.getStatement
              resultSet.close()
              Option(s)
            } else None
          }

          stmt.foreach { s =>
            if (!s.isClosed) s.close()
          }
        }

        private def nextCursor(): Unit = {
          cursor = Sql.unsafeCursor(resultSet, sql.resultSetOnFirstRow, as)
        }

        def onPull(): Unit = cursor match {
          case Some(c) =>
            c.row.as(parser) match {
              case Success(parsed) => {
                counter += 1
                push(out, parsed)
                nextCursor()
              }

              case Failure(cause) =>
                failWith(cause)
            }

          case _ => {
            result.success(counter)
            complete(out)
          }
        }

        override def onDownstreamFinish() = {
          result.tryFailure(new InterruptedException("Downstream finished"))
          release()
          super.onDownstreamFinish()
        }

        setHandler(out, this)
      }

      logic -> result.future
    }
  }

}

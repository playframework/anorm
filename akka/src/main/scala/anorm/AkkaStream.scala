package anorm

import java.sql.Connection

import scala.concurrent.{ Future, Promise }
import akka.stream.{ MaterializationContext, Materializer, Outlet, SourceShape }
import akka.stream.impl.SourceModule
import akka.stream.impl.Stages.DefaultAttributes
import akka.stream.impl.StreamLayout.Module
import akka.stream.scaladsl.{ Sink, Source }
import org.reactivestreams.Publisher

/**
 * Anorm companion for the [[http://doc.akka.io/docs/akka/2.4.4/scala/stream/]].
 *
 * @define materialization It materializes a [[Future]] of [[Int]] containing the number of rows read from the source upon completion,
 *                         and a possible exception if row parsing failed.
 * @define sqlParam the SQL query
 * @define materializerParam the stream materializer
 * @define connectionParam the JDBC connection, which must not be closed until the source is materialized.
 * @define columnAliaserParam the column aliaser
 */
object AkkaStream {
  /**
   * Returns the rows parsed from the `sql` query as a reactive source.
   *
   * $materialization
   *
   * @tparam T the type of the result elements
   * @param sql $sqlParam
   * @param parser the result (row) parser
   * @param as $columnAliaserParam
   * @param m $materializerParam
   * @param connection $connectionParam
   *
   * {{{
   * import java.sql.Connection
   *
   * import akka.NotUsed
   * import akka.stream.Materializer
   * import akka.stream.scaladsl.Source
   *
   * import anorm._
   *
   * def resultSource(implicit m: Materializer, con: Connection): Source[String, NotUsed] = AkkaStream.source(SQL"SELECT * FROM Test", SqlParser.scalar[String], ColumnAliaser.empty)
   * }}}
   */
  def source[T](sql: => Sql, parser: RowParser[T], as: ColumnAliaser)(implicit m: Materializer, con: Connection): Source[T, Future[Int]] = {
    val resultSourceShape = SourceShape[T](Outlet[T]("AnormQueryResult.out"))
    new Source(new ResultSource[T](con, sql, as, parser, resultSourceShape))
  }

  /**
   * Returns the rows parsed from the `sql` query as a reactive source.
   *
   * $materialization
   *
   * @tparam T the type of the result elements
   * @param sql $sqlParam
   * @param parser the result (row) parser
   * @param m $materializerParam
   * @param connection $connectionParam
   */
  def source[T](sql: => Sql, parser: RowParser[T])(implicit m: Materializer, con: Connection): Source[T, Future[Int]] = source[T](sql, parser, ColumnAliaser.empty)

  /**
   * Returns the result rows from the `sql` query as an enumerator.
   * This is equivalent to `source[Row](sql, RowParser.successful, as)`.
   *
   * $materialization
   *
   * @param sql $sqlParam
   * @param as $columnAliaserParam
   * @param m $materializerParam
   * @param connection $connectionParam
   */
  def source(sql: => Sql, as: ColumnAliaser)(implicit m: Materializer, connnection: Connection): Source[Row, Future[Int]] = source(sql, RowParser.successful, as)

  /**
   * Returns the result rows from the `sql` query as an enumerator.
   * This is equivalent to
   * `source[Row](sql, RowParser.successful, ColumnAliaser.empty)`.
   *
   * $materialization
   *
   * @param sql $sqlParam
   * @param m $materializerParam
   * @param connection $connectionParam
   */
  def source(sql: => Sql)(implicit m: Materializer, connnection: Connection): Source[Row, Future[Int]] = source(sql, RowParser.successful, ColumnAliaser.empty)

  // Internal stages

  import scala.util.{ Failure, Success }
  import java.sql.ResultSet
  import akka.stream.{ Attributes, Outlet, SourceShape }
  import akka.stream.stage.{ GraphStage, GraphStageLogic, OutHandler }

  private[anorm] class ResultSource[T](
      connection: Connection,
      sql: Sql,
      as: ColumnAliaser,
      parser: RowParser[T],
      shape: SourceShape[T])(implicit mat: Materializer) extends SourceModule[T, Future[Int]](shape) {

    override def create(context: MaterializationContext): (Publisher[T], Future[Int]) = {
      val result = Promise[Int]()
      (Source.fromGraph(new AnormResult[T](result, connection, sql, as, parser))
        .runWith(Sink.asPublisher(false)), result.future)
    }

    override protected def newInstance(shape: SourceShape[T]): SourceModule[T, Future[Int]] =
      new ResultSource[T](connection, sql, as, parser, shape)

    override def withAttributes(attr: Attributes): Module =
      new ResultSource[T](connection, sql, as, parser, amendShape(attr))

    override def attributes: Attributes = Attributes.name("resultSource") and DefaultAttributes.IODispatcher

    override protected def label: String = s"ResultSource($parser)"
  }

  private[anorm] class AnormResult[T](
      result: Promise[Int],
      connection: Connection,
      sql: Sql,
      as: ColumnAliaser,
      parser: RowParser[T]) extends GraphStage[SourceShape[T]] {

    private[anorm] var resultSet: ResultSet = null

    override val toString = "AnormQueryResult"
    val out: Outlet[T] = Outlet("${toString}.out")
    val shape: SourceShape[T] = SourceShape(out)

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with OutHandler {
        private var cursor: Option[Cursor] = None
        private var counter: Int = 0

        override def preStart() {
          resultSet = sql.unsafeResultSet(connection)
          nextCursor()
        }

        override def postStop() = release()

        private def release() {
          val stmt: Option[java.sql.Statement] = {
            if (resultSet != null && !resultSet.isClosed) {
              val s = resultSet.getStatement
              resultSet.close()
              Some(s)
            } else None
          }

          stmt.foreach { s =>
            if (!s.isClosed) s.close()
          }
        }

        private def nextCursor() {
          cursor = Sql.unsafeCursor(resultSet, sql.resultSetOnFirstRow, as)
        }

        def onPull(): Unit = cursor match {
          case Some(c) => c.row.as(parser) match {
            case Success(parsed) => {
              counter += 1
              push(out, parsed)
              nextCursor()
            }
            case Failure(cause) =>
              result.failure(cause)
              fail(out, cause)
          }

          case _ =>
            result.success(counter)
            complete(out)
        }

        override def onDownstreamFinish() = {
          release()
          super.onDownstreamFinish()
        }

        setHandler(out, this)
      }
  }
}

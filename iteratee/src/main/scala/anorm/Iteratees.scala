/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import java.sql.Connection

import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.iteratee.Enumerator

/** Anorm companion for the [[https://www.playframework.com/documentation/2.4.x/Iteratees]]. */
object Iteratees {
  import scala.util.{ Failure, Success }
  import play.api.libs.iteratee.Concurrent

  /**
   * Returns the rows parsed from the `sql` query as an enumerator.
   *
   * @tparam T the type of the result elements
   * @param sql the SQL query
   * @param parser the result (row) parser
   * @param ec the execution context
   * @param connection the JDBC connection, which must not be closed until the returned enumerator is [[https://www.playframework.com/documentation/2.4.0/api/scala/index.html#play.api.libs.iteratee.Enumerator@onDoneEnumerating%28callback:=%3EUnit%29%28implicitec:scala.concurrent.ExecutionContext%29:play.api.libs.iteratee.Enumerator[E] done]].
   *
   * {{{
   * import java.sql.Connection
   * import scala.concurrent.ExecutionContext.Implicits.global
   * import anorm._
   * import play.api.libs.iteratee._
   *
   * def resultAsEnumerator(implicit con: Connection): Enumerator[String] =
   *   Iteratees.from(SQL"SELECT * FROM Test", SqlParser.scalar[String])
   * }}}
   */
  def from[T](sql: => Sql, parser: RowParser[T])(implicit ec: ExecutionContext, con: Connection): Enumerator[T] = {
    val (resultEnum, chan): (Enumerator[T], Concurrent.Channel[T]) =
      Concurrent.broadcast[T]

    @annotation.tailrec
    def pushToChannel(cursor: Option[Cursor]): Unit = cursor match {
      case Some(cursor) =>
        cursor.row.as(parser) match {
          case Success(elem) => {
            chan.push(elem)
            pushToChannel(cursor.next)
          }
          case Failure(err) => chan.end(err)
        }
      case _ => chan.eofAndEnd()
    }

    Future(sql.withResult(pushToChannel)) // async consumption

    resultEnum
  }

  /**
   * Returns the result rows from the `sql` query as an enumerator.
   *
   * @param sql the SQL query
   * @param ec the execution context
   * @param connection the JDBC connection, which must not be closed until the returned enumerator is [[https://www.playframework.com/documentation/2.4.0/api/scala/index.html#play.api.libs.iteratee.Enumerator@onDoneEnumerating%28callback:=%3EUnit%29%28implicitec:scala.concurrent.ExecutionContext%29:play.api.libs.iteratee.Enumerator[E] done]].
   */
  def from(sql: => Sql)(implicit ec: ExecutionContext, connnection: Connection): Enumerator[Row] =
    from(sql, RowParser.successful)

}

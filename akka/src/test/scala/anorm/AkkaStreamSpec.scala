/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import java.sql.{ Connection, ResultSet }

import scala.collection.immutable.Seq

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem

import akka.stream.Materializer
import akka.stream.scaladsl.{ Keep, Sink, Source }

import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.Implicits._
import acolyte.jdbc.QueryResult
import acolyte.jdbc.RowLists.stringList

import org.specs2.concurrent.ExecutionEnv

final class AkkaStreamSpec(implicit ee: ExecutionEnv) extends org.specs2.mutable.Specification {

  "Akka Stream".title

  implicit lazy val system: ActorSystem = ActorSystem("anorm-tests")

  implicit def materializer: Materializer =
    akka.stream.ActorMaterializer.create(system)

  import StreamTestKit.assertAllStagesStopped

  "Akka Stream" should {
    "expose the query result as source" in assertAllStagesStopped {
      withQueryResult(stringList :+ "A" :+ "B" :+ "C") { implicit con =>
        AkkaStream
          .source(SQL"SELECT * FROM Test", SqlParser.scalar[String])
          .runWith(Sink.seq[String]) must beTypedEqualTo(
          Seq("A", "B", "C")
        ).await(0, 5.seconds)
      }
    }

    "be done if the stream run through" in {
      withQueryResult(stringList :+ "A" :+ "B" :+ "C") { implicit con =>
        AkkaStream
          .source(SQL"SELECT * FROM Test", SqlParser.scalar[String])
          .toMat(Sink.ignore)(Keep.left)
          .run() must beTypedEqualTo(3).await(0, 3.seconds)
      }
    }

    "fail materialized value on finished downstream" in assertAllStagesStopped {
      val list = stringList :+ "A" :+ "B" :+ "C"

      withQueryResult(list.withCycling(true)) { implicit con =>
        val killSwitch = akka.stream.KillSwitches.shared("cycling-switch")

        AkkaStream
          .source(SQL"SELECT * FROM Test", SqlParser.scalar[String])

        val p = scala.concurrent.Promise[Int]()

        val res = AkkaStream
          .source(SQL"SELECT * FROM Test", SqlParser.scalar[String])
          .mapMaterializedValue(p.completeWith)
          .via(killSwitch.flow)
          .runWith(Sink.ignore)
          .flatMap(_ => p.future)

        Thread.sleep(2000)
        killSwitch.shutdown()

        res must throwA[java.util.concurrent.ExecutionException].like {
          case e => e.getCause must beAnInstanceOf[InterruptedException]
        }.await
      }
    }

    "manage resources" >> {
      def run[T](sink: Sink[String, T])(implicit c: Connection) = {
        val graph = source(SQL"SELECT * FROM Test", SqlParser.scalar[String])

        Source
          .fromGraph(graph)
          .runWith(sink.mapMaterializedValue { _ =>
            Option(graph.resultSet)
          })
      }

      def runAsync[T](sink: Sink[String, Future[T]])(implicit c: Connection) = {
        val graph = source(SQL"SELECT * FROM Test", SqlParser.scalar[String])

        Source.fromGraph(graph).runWith(sink).map { _ => graph.resultSet }
      }

      "on success" in assertAllStagesStopped {
        withQueryResult(stringList :+ "A" :+ "B" :+ "C") { implicit con =>
          runAsync(Sink.seq[String]) must beLike[ResultSet] {
            case rs =>
              (rs.isClosed must beTrue).and(rs.getStatement.isClosed must beTrue).and(con.isClosed must beFalse)
          }.await(0, 5.seconds)
        }
      }

      "on cancellation" in withQueryResult(stringList :+ "A" :+ "B" :+ "C") { implicit con =>
        assertAllStagesStopped {
          val rSet: Option[ResultSet] = run(Sink.cancelled[String])

          (rSet must beNone).or(rSet must beSome[ResultSet].which { rs =>
            (rs.isClosed must beTrue).and(rs.getStatement.isClosed must beTrue)
          })
        }
      }

      "on failed initialization" in {
        import java.sql.SQLException

        withQueryResult(QueryResult.Nil) { implicit con =>
          val failingSql = new Sql {
            import java.sql.PreparedStatement

            def unsafeStatement(
                connection: Connection,
                generatedColumn: String,
                generatedColumns: AkkaCompat.Seq[String]
            ): PreparedStatement = ???

            def unsafeStatement(connection: Connection, getGeneratedKeys: Boolean): PreparedStatement =
              throw new SQLException("Init failure")

            def resultSetOnFirstRow: Boolean = ???
          }

          val graph = source(failingSql, SqlParser.scalar[String])
          val mat   = Source.fromGraph(graph).toMat(Sink.ignore)(Keep.left).run()

          mat must throwA[SQLException]("Init failure").awaitFor(3.seconds)
        }
      }

      "on failure" in withQueryResult(stringList :+ "A" :+ "B" :+ "C") { implicit con =>
        assertAllStagesStopped {
          val rSet = run(Sink.reduce[String] { (_, _) => sys.error("Foo") })

          (rSet must beNone).or(rSet must beSome[ResultSet].which { rs =>
            (rs must beNull).or(rs.isClosed must beTrue)
          })
        }
      }
    }
  }

  def source[T](sql: Sql, parser: RowParser[T])(implicit connection: Connection) =
    new AkkaStream.ResultSource[T](connection, sql, ColumnAliaser.empty, parser)
}

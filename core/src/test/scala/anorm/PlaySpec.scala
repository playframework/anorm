import scala.util.{ Failure, Success }
import scala.concurrent.Future

import play.api.libs.iteratee.{ Concurrent, Enumerator, Input, Iteratee }

import anorm.{ SqlParser, _ }

import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.RowLists.stringList
import acolyte.jdbc.Implicits._

// TODO: Move in a separate module
object PlaySpec extends org.specs2.mutable.Specification {
  "Play integration" title

  "Iteratees" should {
    "broadcast the streaming result" in {
      val rowParser = SqlParser.scalar[String]
      val (resultEnum, chan): (Enumerator[String], Concurrent.Channel[String]) =
        Concurrent.broadcast[String]
      val futureRes: Future[List[String]] =
        resultEnum.run(Iteratee.getChunks[String])

      @annotation.tailrec
      def pushToChannel(cursor: Option[Cursor]): Unit = cursor match {
        case Some(cursor) => cursor.row.as(rowParser) match {
          case Success(elem) => {
            chan.push(elem)
            pushToChannel(cursor.next)
          }
          case Failure(err) => chan.end(err)
        }
        case _ => chan.eofAndEnd()
      }

      Future {
        Thread.sleep(500)
        withQueryResult(stringList :+ "A" :+ "B" :+ "C") { implicit c =>
          SQL"SELECT * FROM Test".withResult(pushToChannel)
        }
      }

      chan.push("_") // before the async data from result      

      futureRes must beEqualTo(List("_", "A", "B", "C")).await(1000)
    }
  }
}

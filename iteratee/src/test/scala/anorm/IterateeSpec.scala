import scala.concurrent.Future
import scala.concurrent.duration._

import org.specs2.concurrent.{ ExecutionEnv => EE }

import play.api.libs.iteratee.Iteratee

import anorm._

import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.RowLists.stringList
import acolyte.jdbc.Implicits._

class IterateeSpec extends org.specs2.mutable.Specification {
  "Play Iteratee" title

  "Iteratees" should {
    "broadcast the streaming result" in { implicit ee: EE =>
      withQueryResult(stringList :+ "A" :+ "B" :+ "C") { implicit con =>
        Iteratees.from(SQL"SELECT * FROM Test", SqlParser.scalar[String]).
          run(Iteratee.getChunks[String]) must beEqualTo(List("A", "B", "C")).
          await(0, 5.second)
      }
    }
  }
}

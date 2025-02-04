/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

import scala.concurrent.duration._

import play.api.libs.iteratee.Iteratee

import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.Implicits._
import acolyte.jdbc.RowLists.stringList

import org.specs2.concurrent.ExecutionEnv

import anorm._

class IterateeSpec(implicit ee: ExecutionEnv) extends org.specs2.mutable.Specification {

  "Play Iteratee".title

  "Iteratees" should {
    "broadcast the streaming result" in {
      withQueryResult(stringList :+ "A" :+ "B" :+ "C") { implicit con =>
        Iteratees
          .from(SQL"SELECT * FROM Test", SqlParser.scalar[String])
          .run(Iteratee.getChunks[String]) must beEqualTo(List("A", "B", "C")).await(0, 5.second)
      }
    }
  }
}

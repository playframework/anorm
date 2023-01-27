/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.Implicits._
import acolyte.jdbc.RowLists

private[anorm] trait AnormCompatSpec { spec: AnormSpec =>
  "Query (scala2)" should {
    "be executed as simple SQL" in withQueryResult(RowLists.booleanList :+ true) { implicit con =>
      val sql = SQL("SELECT 1")

      (implicitly[Sql](sql).aka("converted") must beAnInstanceOf[SimpleSql[_]])
        .and(SQL("SELECT 1").execute().aka("executed") must beTrue)
    }
  }
}

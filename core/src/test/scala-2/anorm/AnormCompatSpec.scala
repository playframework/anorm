package anorm

import acolyte.jdbc.Implicits._

import acolyte.jdbc.RowLists
import acolyte.jdbc.AcolyteDSL.withQueryResult

private[anorm] trait AnormCompatSpec { spec: AnormSpec =>
  "Query (scala2)" should {
    "be executed as simple SQL" in withQueryResult(RowLists.booleanList :+ true) { implicit con =>
      val sql = SQL("SELECT 1")

      (implicitly[Sql](sql).aka("converted") must beAnInstanceOf[SimpleSql[_]])
        .and(SQL("SELECT 1").execute().aka("executed") must beTrue)
    }
  }
}

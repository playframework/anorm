package anorm

import java.time.Instant

import acolyte.jdbc.QueryResult
import acolyte.jdbc.RowLists.{
  dateList,
  longList,
  timeList,
  timestampList
}
import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.Implicits._

object Java8ColumnSpec extends org.specs2.mutable.Specification {
  "Java 8 Column" title

  import SqlParser.scalar
  import Java8._

  "Column mapped as Java8 instant" should {
    val instant = Instant.now
    val time = instant.toEpochMilli

    "be parsed from date" in withQueryResult(
      dateList :+ new java.sql.Date(time)) { implicit con =>
        SQL("SELECT d").as(scalar[Instant].single).
          aka("parsed instant") must_== instant
      }

    "be parsed from time" in withQueryResult(
      timeList :+ new java.sql.Time(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[Instant].single).
          aka("parsed instant") must_== instant
      }

    "be parsed from timestamp" in withQueryResult(
      timestampList :+ new java.sql.Timestamp(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[Instant].single).
          aka("parsed instant") must_== instant
      }

    "be parsed from numeric time" in withQueryResult(longList :+ time) {
      implicit con =>
        SQL("SELECT time").as(scalar[Instant].single).
          aka("parsed instant") must_== instant

    }
  }
}

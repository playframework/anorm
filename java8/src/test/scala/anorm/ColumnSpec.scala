package anorm

import java.time.{ Instant, LocalDateTime, ZonedDateTime, ZoneId }

import acolyte.jdbc.QueryResult
import acolyte.jdbc.RowLists.{
  rowList1,
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

  "Column mapped as Java8 instant" should {
    val instant = Instant.now
    val time = instant.toEpochMilli

    shapeless.test.illTyped("implicitly[Column[Instant]]")

    import Java8._

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

    "be parsed from timestamp wrapper" >> {
      "with not null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ tsw1(time)) { implicit con =>
          SQL("SELECT ts").as(scalar[Instant].single).
            aka("parsed instant") must_== instant
        }

      "with null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ null.asInstanceOf[TWrapper]) {
          implicit con =>
            SQL("SELECT ts").as(scalar[Instant].singleOpt).
              aka("parsed instant") must beNone
        }
    }
  }

  "Column mapped as Java8 local date/time" should {
    val instant = Instant.now
    val date = LocalDateTime.ofInstant(instant, ZoneId.systemDefault)
    val time = instant.toEpochMilli

    shapeless.test.illTyped("implicitly[Column[LocalDateTime]]")

    import Java8._

    "be parsed from date" in withQueryResult(
      dateList :+ new java.sql.Date(time)) { implicit con =>
        SQL("SELECT d").as(scalar[LocalDateTime].single).
          aka("parsed local date/time") must_== date
      }

    "be parsed from time" in withQueryResult(
      timeList :+ new java.sql.Time(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[LocalDateTime].single).
          aka("parsed local date/time") must_== date
      }

    "be parsed from timestamp" in withQueryResult(
      timestampList :+ new java.sql.Timestamp(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[LocalDateTime].single).
          aka("parsed local date/time") must_== date
      }

    "be parsed from numeric time" in withQueryResult(longList :+ time) {
      implicit con =>
        SQL("SELECT time").as(scalar[LocalDateTime].single).
          aka("parsed local date/time") must_== date

    }

    "be parsed from timestamp wrapper" >> {
      "with not null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ tsw1(time)) { implicit con =>
          SQL("SELECT ts").as(scalar[LocalDateTime].single).
            aka("parsed local date/time") must_== date
        }

      "with null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ null.asInstanceOf[TWrapper]) {
          implicit con =>
            SQL("SELECT ts").as(scalar[LocalDateTime].singleOpt).
              aka("parsed local date/time") must beNone
        }
    }
  }

  "Column mapped as Java8 zoned date/time" should {
    val instant = Instant.now
    val date = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault)
    val time = instant.toEpochMilli

    shapeless.test.illTyped("implicitly[Column[ZonedDateTime]]")

    import Java8._

    "be parsed from date" in withQueryResult(
      dateList :+ new java.sql.Date(time)) { implicit con =>
        SQL("SELECT d").as(scalar[ZonedDateTime].single).
          aka("parsed zoned date/time") must_== date
      }

    "be parsed from time" in withQueryResult(
      timeList :+ new java.sql.Time(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[ZonedDateTime].single).
          aka("parsed zoned date/time") must_== date
      }

    "be parsed from timestamp" in withQueryResult(
      timestampList :+ new java.sql.Timestamp(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[ZonedDateTime].single).
          aka("parsed zoned date/time") must_== date
      }

    "be parsed from numeric time" in withQueryResult(longList :+ time) {
      implicit con =>
        SQL("SELECT time").as(scalar[ZonedDateTime].single).
          aka("parsed zoned date/time") must_== date

    }

    "be parsed from timestamp wrapper" >> {
      "with not null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ tsw1(time)) { implicit con =>
          SQL("SELECT ts").as(scalar[ZonedDateTime].single).
            aka("parsed zoned date/time") must_== date
        }

      "with null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ null.asInstanceOf[TWrapper]) {
          implicit con =>
            SQL("SELECT ts").as(scalar[ZonedDateTime].singleOpt).
              aka("parsed zoned date/time") must beNone
        }
    }
  }

  trait TWrapper { def getTimestamp: java.sql.Timestamp }
  def tsw1(time: Long) = new TWrapper {
    lazy val getTimestamp = new java.sql.Timestamp(time)
  }
}

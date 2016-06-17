package anorm

import java.sql.{ Date, Timestamp }

import org.joda.time.{ DateTime, LocalDate, LocalDateTime, Instant }

import org.specs2.mutable.Specification

import acolyte.jdbc.RowLists.{
  dateList,
  longList,
  rowList1,
  timestampList,
  timeList
}
import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.Implicits._

import SqlParser.scalar

trait JodaColumnSpec { specs: Specification =>
  "Column mapped as Joda instant" should {
    val time = Instant.now()

    "be parsed from date" in withQueryResult(
      dateList :+ new Date(time.getMillis)) { implicit con =>
        SQL("SELECT d").as(scalar[Instant].single).
          aka("parsed instant") must_== time
      }

    "be parsed from timestamp" in withQueryResult(
      timestampList :+ new Timestamp(time.getMillis)) { implicit con =>
        SQL("SELECT ts").as(scalar[Instant].single).
          aka("parsed instant") must beLike {
            case d => d aka "time" must_== time
          }
      }

    "be parsed from time" in withQueryResult(longList :+ time.getMillis) {
      implicit con =>
        SQL("SELECT time").as(scalar[Instant].single).
          aka("parsed instant") must_== time
    }

    "be parsed from timestamp wrapper" >> {
      "with not null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ tsw1(time.getMillis)) { implicit con =>
          SQL("SELECT ts").as(scalar[Instant].single).
            aka("parsed instant") must_== time
        }

      "with null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ null.asInstanceOf[TWrapper]) {
          implicit con =>
            SQL("SELECT ts").as(scalar[Instant].singleOpt).
              aka("parsed instant") must beNone
        }
    }
  }

  "Column mapped as Joda date/time" should {
    val time = System.currentTimeMillis
    val date = new DateTime(time)

    "be parsed from date" in withQueryResult(
      dateList :+ new java.sql.Date(time)) { implicit con =>
        SQL("SELECT d").as(scalar[DateTime].single).
          aka("parsed local date/time") must_== date
      }

    "be parsed from time" in withQueryResult(
      timeList :+ new java.sql.Time(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[DateTime].single).
          aka("parsed local date/time") must_== date
      }

    "be parsed from timestamp" in withQueryResult(
      timestampList :+ new java.sql.Timestamp(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[DateTime].single).
          aka("parsed local date/time") must_== date
      }

    "be parsed from numeric time" in withQueryResult(longList :+ time) {
      implicit con =>
        SQL("SELECT time").as(scalar[DateTime].single).
          aka("parsed local date/time") must_== date

    }

    "be parsed from timestamp wrapper" >> {
      "with not null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ tsw1(time)) { implicit con =>
          SQL("SELECT ts").as(scalar[DateTime].single).
            aka("parsed local date/time") must_== date
        }

      "with null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ null.asInstanceOf[TWrapper]) {
          implicit con =>
            SQL("SELECT ts").as(scalar[DateTime].singleOpt).
              aka("parsed local date/time") must beNone
        }
    }
  }

  "Column mapped as Joda local date/time" should {
    val time = System.currentTimeMillis
    val date = new LocalDateTime(time)

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

  "Column mapped as Joda local date" should {
    val time = System.currentTimeMillis
    val date = new LocalDate(time)

    "be parsed from date" in withQueryResult(
      dateList :+ new java.sql.Date(time)) { implicit con =>
        SQL("SELECT d").as(scalar[LocalDate].single).
          aka("parsed local date/time") must_== date
      }

    "be parsed from time" in withQueryResult(
      timeList :+ new java.sql.Time(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[LocalDate].single).
          aka("parsed local date/time") must_== date
      }

    "be parsed from timestamp" in withQueryResult(
      timestampList :+ new java.sql.Timestamp(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[LocalDate].single).
          aka("parsed local date/time") must_== date
      }

    "be parsed from numeric time" in withQueryResult(longList :+ time) {
      implicit con =>
        SQL("SELECT time").as(scalar[LocalDate].single).
          aka("parsed local date/time") must_== date

    }

    "be parsed from timestamp wrapper" >> {
      "with not null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ tsw1(time)) { implicit con =>
          SQL("SELECT ts").as(scalar[LocalDate].single).
            aka("parsed local date/time") must_== date
        }

      "with null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ null.asInstanceOf[TWrapper]) {
          implicit con =>
            SQL("SELECT ts").as(scalar[LocalDate].singleOpt).
              aka("parsed local date/time") must beNone
        }
    }
  }

  trait TWrapper { def getTimestamp: java.sql.Timestamp }
  def tsw1(time: Long) = new TWrapper {
    lazy val getTimestamp = new java.sql.Timestamp(time)
  }
}

trait JodaParameterSpec { specs: ParameterSpec =>
  import JodaParameterMetaData._

  lazy val dateTime1 = new DateTime(Date1.getTime)
  lazy val localDateTime1 = new LocalDateTime(Date1.getTime)
  lazy val localDate1 = new LocalDate(Date1.getTime)
  lazy val instant1 = new Instant(Date1.getTime)

  "Named parameters" should {
    "be Joda date/time" in withConnection() { implicit c =>
      SQL("set-date {p}").on("p" -> dateTime1).execute() must beFalse
    }

    "be null Joda date/time" in withConnection() { implicit c =>
      SQL("set-null-date {p}").
        on("p" -> null.asInstanceOf[DateTime]).execute() must beFalse
    }

    "be undefined Joda date/time" in withConnection() { implicit c =>
      SQL("set-null-date {p}").
        on("p" -> (None: Option[DateTime])).execute() must beFalse
    }

    "be Joda local date/time" in withConnection() { implicit c =>
      SQL("set-date {p}").on("p" -> localDateTime1).execute() must beFalse
    }

    "be null Joda local date/time" in withConnection() { implicit c =>
      SQL("set-null-date {p}").
        on("p" -> null.asInstanceOf[LocalDateTime]).execute() must beFalse
    }

    "be undefined Joda local date/time" in withConnection() { implicit c =>
      SQL("set-null-date {p}").
        on("p" -> (None: Option[LocalDateTime])).execute() must beFalse
    }

    "be Joda local date" in withConnection() { implicit c =>
      SQL("set-local-date {p}").on("p" -> localDate1).execute() must beFalse
    }

    "be null Joda local date" in withConnection() { implicit c =>
      SQL("set-null-date {p}").
        on("p" -> null.asInstanceOf[LocalDate]).execute() must beFalse
    }

    "be undefined Joda local date" in withConnection() { implicit c =>
      SQL("set-null-date {p}").
        on("p" -> (None: Option[LocalDate])).execute() must beFalse
    }

    "be Joda instant" in withConnection() { implicit c =>
      SQL("set-date {p}").on("p" -> instant1).execute() must beFalse
    }

    "be null Joda Instant" in withConnection() { implicit c =>
      SQL("set-null-date {p}").
        on("p" -> null.asInstanceOf[Instant]).execute() must beFalse
    }

    "be undefined Joda instant" in withConnection() { implicit c =>
      SQL("set-null-date {p}").
        on("p" -> (None: Option[Instant])).execute() must beFalse
    }
  }
}

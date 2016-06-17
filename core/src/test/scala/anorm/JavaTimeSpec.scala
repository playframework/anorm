package anorm

import java.time.{ ZonedDateTime, ZoneId, LocalDate, LocalDateTime, Instant }

import acolyte.jdbc.AcolyteDSL._
import acolyte.jdbc.RowLists._
import acolyte.jdbc.Implicits._
import org.specs2.mutable.Specification

class JavaTimeColumnSpec extends Specification {
  import SqlParser.scalar

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

  "Column mapped as Java8 local date" should {
    val instant = Instant.now
    val date = LocalDate.now()
    val time = instant.toEpochMilli

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

  "Column mapped as Java8 zoned date/time" should {
    val instant = Instant.now
    val date = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault)
    val time = instant.toEpochMilli

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

object JavaTimeParameterMetaDataSpec extends Specification {
  "Metadata" should {
    "be provided for parameter" >> {
      s"of type Instant" in {
        Option(implicitly[ParameterMetaData[Instant]].sqlType).
          aka("SQL type") must beSome
      }

      s"of type LocalDateTime" in {
        Option(implicitly[ParameterMetaData[LocalDateTime]].sqlType).
          aka("SQL type") must beSome
      }

      s"of type LocalDate" in {
        Option(implicitly[ParameterMetaData[LocalDate]].sqlType).
          aka("SQL type") must beSome
      }

      s"of type ZonedDateTime" in {
        Option(implicitly[ParameterMetaData[ZonedDateTime]].sqlType).
          aka("SQL type") must beSome
      }
    }
  }
}

object JavaTimeParameterSpec extends Specification {

  import acolyte.jdbc.{
    DefinedParameter => DParam,
    ParameterMetaData => ParamMeta,
    UpdateExecution
  }

  val Instant1 = Instant.ofEpochSecond(123456789)
  val LocalDateTime1 = LocalDateTime.ofInstant(Instant1, ZoneId.of("UTC"))
  val LocalDateTime1Epoch = LocalDateTime1.atZone(ZoneId.systemDefault()).toEpochSecond * 1000
  val ZonedDateTime1 = ZonedDateTime.ofInstant(Instant1, ZoneId.of("UTC"))
  val SqlTimestamp = ParamMeta.Timestamp

  def withJavaTimeConnection[A](ps: (String, String)*)(f: java.sql.Connection => A): A = f(connection(handleStatement withUpdateHandler {
    case UpdateExecution("set-instant ?",
      DParam(t: java.sql.Timestamp, SqlTimestamp) :: Nil) if t.getTime == 123456789000L => 1 /* case ok */
    case UpdateExecution("set-null-instant ?",
      DParam(null, SqlTimestamp) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-local-date-time ?",
      DParam(t: java.sql.Timestamp, SqlTimestamp) :: Nil) if t.getTime == LocalDateTime1Epoch => 1 /* case ok */
    case UpdateExecution("set-null-local-date-time ?",
      DParam(null, SqlTimestamp) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-zoned-date-time ?",
      DParam(t: java.sql.Timestamp, SqlTimestamp) :: Nil) if t.getTime == 123456789000L => 1 /* case ok */
    case UpdateExecution("set-null-zoned-date-time ?",
      DParam(null, SqlTimestamp) :: Nil) => 1 /* case ok */
  }, ps: _*))

  "Java time named parameters" should {
    "be instant" in withJavaTimeConnection() { implicit c =>
      SQL("set-instant {p}").on("p" -> Instant1).execute() must beFalse
    }

    "be null instant" in withJavaTimeConnection() { implicit c =>
      SQL("set-null-instant {p}").
        on("p" -> null.asInstanceOf[Instant]).execute() must beFalse
    }

    "be undefined instant" in withJavaTimeConnection() { implicit c =>
      SQL("set-null-instant {p}").
        on("p" -> Option.empty[Instant]).execute() must beFalse
    }

    "be local date/time" in withJavaTimeConnection() { implicit c =>
      SQL("set-local-date-time {p}").on("p" -> LocalDateTime1).
        execute() must beFalse
    }

    "be null local date/time" in withJavaTimeConnection() { implicit c =>
      SQL("set-null-local-date-time {p}").
        on("p" -> null.asInstanceOf[LocalDateTime]).execute() must beFalse
    }

    "be undefined local date/time" in withJavaTimeConnection() { implicit c =>
      SQL("set-null-local-date-time {p}").
        on("p" -> Option.empty[LocalDateTime]).execute() must beFalse
    }

    "be zoned date/time" in withJavaTimeConnection() { implicit c =>
      SQL("set-zoned-date-time {p}").on("p" -> ZonedDateTime1).
        execute() must beFalse
    }

    "be null zoned date/time" in withJavaTimeConnection() { implicit c =>
      SQL("set-null-zoned-date-time {p}").
        on("p" -> null.asInstanceOf[ZonedDateTime]).execute() must beFalse
    }

    "be undefined zoned date/time" in withJavaTimeConnection() { implicit c =>
      SQL("set-null-zoned-date-time {p}").
        on("p" -> Option.empty[ZonedDateTime]).execute() must beFalse
    }
  }

  "Java time parameter in order" should {
    "be one instant" in withJavaTimeConnection() { implicit c =>
      SQL("set-instant {p}").onParams(pv(Instant1)).execute() must beFalse
    }

    "be one local date/time" in withJavaTimeConnection() { implicit c =>
      SQL("set-local-date-time {p}").onParams(
        pv(LocalDateTime1)).execute() must beFalse
    }

    "be one zoned date/time" in withJavaTimeConnection() { implicit c =>
      SQL("set-zoned-date-time {p}").onParams(
        pv(ZonedDateTime1)).execute() must beFalse
    }
  }

  private def pv[A](v: A)(implicit s: ToSql[A] = null, p: ToStatement[A]) =
    ParameterValue(v, s, p)
}


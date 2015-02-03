package anorm

import java.time.{ Instant, LocalDateTime, ZonedDateTime, ZoneId }

import acolyte.jdbc.{
  DefinedParameter => DParam,
  ParameterMetaData => ParamMeta,
  UpdateExecution
}
import acolyte.jdbc.AcolyteDSL.{ connection, handleStatement }
import acolyte.jdbc.Implicits._

object Java8ParameterSpec extends org.specs2.mutable.Specification {
  "Java 8 Parameter" title

  import Java8._

  val Instant1 = Instant.ofEpochSecond(123456789)
  val LocalDateTime1 = LocalDateTime.ofInstant(Instant1, ZoneId.of("UTC"))
  val ZonedDateTime1 = ZonedDateTime.ofInstant(Instant1, ZoneId.of("UTC"))
  val SqlTimestamp = ParamMeta.Timestamp

  def withConnection[A](ps: (String, String)*)(f: java.sql.Connection => A): A = f(connection(handleStatement withUpdateHandler {
    case UpdateExecution("set-instant ?",
      DParam(t: java.sql.Timestamp, SqlTimestamp) :: Nil) if (t.getTime == 123456789000L) => 1 /* case ok */
    case UpdateExecution("set-null-instant ?",
      DParam(null, SqlTimestamp) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-local-date-time ?",
      DParam(t: java.sql.Timestamp, SqlTimestamp) :: Nil) if (
      t.getTime == 123453189000L) => 1 /* case ok */
    case UpdateExecution("set-null-local-date-time ?",
      DParam(null, SqlTimestamp) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-zoned-date-time ?",
      DParam(t: java.sql.Timestamp, SqlTimestamp) :: Nil) if (
      t.getTime == 123456789000L) => 1 /* case ok */
    case UpdateExecution("set-null-zoned-date-time ?",
      DParam(null, SqlTimestamp) :: Nil) => 1 /* case ok */

  }, ps: _*))

  "Named parameters" should {
    "be instant" in withConnection() { implicit c =>
      SQL("set-instant {p}").on("p" -> Instant1).execute() must beFalse
    }

    "be null instant" in withConnection() { implicit c =>
      SQL("set-null-instant {p}").
        on("p" -> null.asInstanceOf[Instant]).execute() must beFalse
    }

    "be undefined instant" in withConnection() { implicit c =>
      SQL("set-null-instant {p}").
        on("p" -> (Option.empty[Instant])).execute() must beFalse
    }

    "be local date/time" in withConnection() { implicit c =>
      SQL("set-local-date-time {p}").on("p" -> LocalDateTime1).
        execute() must beFalse
    }

    "be null local date/time" in withConnection() { implicit c =>
      SQL("set-null-local-date-time {p}").
        on("p" -> null.asInstanceOf[LocalDateTime]).execute() must beFalse
    }

    "be undefined local date/time" in withConnection() { implicit c =>
      SQL("set-null-local-date-time {p}").
        on("p" -> (Option.empty[LocalDateTime])).execute() must beFalse
    }

    "be zoned date/time" in withConnection() { implicit c =>
      SQL("set-zoned-date-time {p}").on("p" -> ZonedDateTime1).
        execute() must beFalse
    }

    "be null zoned date/time" in withConnection() { implicit c =>
      SQL("set-null-zoned-date-time {p}").
        on("p" -> null.asInstanceOf[ZonedDateTime]).execute() must beFalse
    }

    "be undefined zoned date/time" in withConnection() { implicit c =>
      SQL("set-null-zoned-date-time {p}").
        on("p" -> (Option.empty[ZonedDateTime])).execute() must beFalse
    }
  }

  "Parameter in order" should {
    "be one instant" in withConnection() { implicit c =>
      SQL("set-instant {p}").onParams(pv(Instant1)).execute() must beFalse
    }

    "be one local date/time" in withConnection() { implicit c =>
      SQL("set-local-date-time {p}").onParams(
        pv(LocalDateTime1)).execute() must beFalse
    }

    "be one zoned date/time" in withConnection() { implicit c =>
      SQL("set-zoned-date-time {p}").onParams(
        pv(ZonedDateTime1)).execute() must beFalse
    }
  }

  private def pv[A](v: A)(implicit s: ToSql[A] = null, p: ToStatement[A]) =
    ParameterValue(v, s, p)
}

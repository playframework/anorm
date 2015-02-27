package anorm

import java.sql.{ PreparedStatement, Timestamp, Types }
import java.time.{ Instant, LocalDateTime, ZonedDateTime, ZoneId }

/** Provides Java8 specific typeclasses. */
object Java8 {
  /**
   * Sets a temporal instant on statement.
   *
   * {{{
   * import java.time.Instant
   * import anorm.Java8._
   *
   * SQL("SELECT * FROM Test WHERE time < {b}").on('b -> Instant.now)
   * }}}
   */
  implicit def instantToStatement(implicit meta: ParameterMetaData[Instant]): ToStatement[Instant] = new ToStatement[Instant] {
    def set(s: PreparedStatement, i: Int, t: Instant): Unit =
      if (t == null) s.setNull(i, meta.jdbcType)
      else s.setTimestamp(i, Timestamp from t)
  }

  /**
   * Sets a local date/time on statement.
   *
   * {{{
   * import java.time.LocalDateTime
   * import anorm.Java8._
   *
   * SQL("SELECT * FROM Test WHERE time < {b}").on('b -> LocalDateTime.now)
   * }}}
   */
  implicit def localDateTimeToStatement(implicit meta: ParameterMetaData[LocalDateTime]): ToStatement[LocalDateTime] = new ToStatement[LocalDateTime] {
    def set(s: PreparedStatement, i: Int, t: LocalDateTime): Unit =
      if (t == null) s.setNull(i, meta.jdbcType)
      else s.setTimestamp(i, Timestamp valueOf t)
  }

  /**
   * Sets a zoned date/time on statement.
   *
   * {{{
   * import java.time.ZonedDateTime
   * import anorm.Java8._
   *
   * SQL("SELECT * FROM Test WHERE time < {b}").on('b -> ZonedDateTime.now)
   * }}}
   */
  implicit def zonedDateTimeToStatement(implicit meta: ParameterMetaData[ZonedDateTime]): ToStatement[ZonedDateTime] = new ToStatement[ZonedDateTime] {
    def set(s: PreparedStatement, i: Int, t: ZonedDateTime): Unit =
      if (t == null) s.setNull(i, meta.jdbcType)
      else s.setTimestamp(i, Timestamp from t.toInstant)
  }

  /** Parameter metadata for Java8 instant */
  implicit object InstantParameterMetaData extends ParameterMetaData[Instant] {
    val sqlType = "TIMESTAMP"
    val jdbcType = Types.TIMESTAMP
  }

  /** Parameter metadata for Java8 local date/time */
  implicit object LocalDateTimeParameterMetaData
      extends ParameterMetaData[LocalDateTime] {

    val sqlType = "TIMESTAMP"
    val jdbcType = Types.TIMESTAMP
  }

  /** Parameter metadata for Java8 zoned date/time */
  implicit object ZonedDateTimeParameterMetaData
      extends ParameterMetaData[ZonedDateTime] {

    val sqlType = "TIMESTAMP"
    val jdbcType = Types.TIMESTAMP
  }

  /**
   * Parses column as Java8 instant.
   * Time zone offset is the one of default JVM time zone
   * (see [[java.time.ZoneId.systemDefault]]).
   *
   * {{{
   * import java.time.Instant
   * import anorm.Java8._
   *
   * val i: Instant = SQL("SELECT last_mod FROM tbl").as(scalar[Instant].single)
   * }}}
   */
  implicit val columnToInstant: Column[Instant] =
    Column.nonNull1 { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case date: java.util.Date => Right(Instant ofEpochMilli date.getTime)
        case time: Long => Right(Instant ofEpochMilli time)
        case tsw: TimestampWrapper1 =>
          Option(tsw.getTimestamp).fold(Right(null.asInstanceOf[Instant]))(t =>
            Right(Instant ofEpochMilli t.getTime))
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to Java8 Instant for column $qualified"))
      }
    }

  /**
   * Parses column as Java8 local date/time.
   * Time zone offset is the one of default JVM time zone
   * (see [[java.time.ZoneId.systemDefault]]).
   *
   * {{{
   * import java.time.LocalDateTime
   * import anorm.Java8._
   *
   * val i: LocalDateTime = SQL("SELECT last_mod FROM tbl").
   *   as(scalar[LocalDateTime].single)
   * }}}
   */
  implicit val columnToLocalDateTime: Column[LocalDateTime] = {
    @inline def dateTime(ts: Long) = LocalDateTime.ofInstant(
      Instant.ofEpochMilli(ts), ZoneId.systemDefault)

    Column.nonNull1 { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case date: java.util.Date => Right(dateTime(date.getTime))
        case time: Long => Right(dateTime(time))
        case tsw: TimestampWrapper1 => Option(tsw.getTimestamp).
          fold(Right(null.asInstanceOf[LocalDateTime]))(t =>
            Right(dateTime(t.getTime)))
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to Java8 LocalDateTime for column $qualified"))
      }
    }
  }

  /**
   * Parses column as Java8 zoned date/time.
   * Time zone offset is the one of default JVM time zone
   * (see [[java.time.ZoneId.systemDefault]]).
   *
   * {{{
   * import java.time.ZonedDateTime
   * import anorm.Java8._
   *
   * val i: ZonedDateTime = SQL("SELECT last_mod FROM tbl").
   *   as(scalar[ZonedDateTime].single)
   * }}}
   */
  implicit val columnToZonedDateTime: Column[ZonedDateTime] = {
    @inline def dateTime(ts: Long) = ZonedDateTime.ofInstant(
      Instant.ofEpochMilli(ts), ZoneId.systemDefault)

    Column.nonNull1 { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case date: java.util.Date => Right(dateTime(date.getTime))
        case time: Long => Right(dateTime(time))
        case tsw: TimestampWrapper1 => Option(tsw.getTimestamp).
          fold(Right(null.asInstanceOf[ZonedDateTime]))(t =>
            Right(dateTime(t.getTime)))
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to Java8 ZonedDateTime for column $qualified"))
      }
    }
  }
}

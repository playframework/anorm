/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import java.sql.{ PreparedStatement, Timestamp }

/** Meta data for Joda parameters */
object JodaParameterMetaData {
  import org.joda.time.{ DateTime, Instant, LocalDate, LocalDateTime }

  import java.sql.Types

  sealed trait JodaTimeMetaData {
    val sqlType  = "TIMESTAMP"
    val jdbcType = Types.TIMESTAMP
  }

  /** Date/time parameter meta data */
  implicit object JodaDateTimeMetaData extends ParameterMetaData[DateTime] with JodaTimeMetaData

  /** Local date/time parameter meta data */
  implicit object JodaLocalDateTimeMetaData extends ParameterMetaData[LocalDateTime] with JodaTimeMetaData

  /** Instant parameter meta data */
  implicit object JodaInstantMetaData extends ParameterMetaData[Instant] with JodaTimeMetaData

  /** Local date parameter meta data */
  implicit object JodaLocalDateMetaData extends ParameterMetaData[LocalDate] with JodaTimeMetaData
}

trait JodaToStatement {
  import org.joda.time.{ DateTime, Instant, LocalDate, LocalDateTime }

  /**
   * Sets joda-time DateTime as statement parameter.
   * For `null` value, `setNull` with `TIMESTAMP` is called on statement.
   */
  implicit def jodaDateTimeToStatement(implicit meta: ParameterMetaData[DateTime]): ToStatement[DateTime] =
    new ToStatement[DateTime] {
      def set(s: PreparedStatement, index: Int, date: DateTime): Unit =
        if (date != (null: DateTime)) {
          s.setTimestamp(index, new Timestamp(date.getMillis()))
        } else s.setNull(index, meta.jdbcType)
    }

  /**
   * Sets a local date/time on statement.
   */
  implicit def jodaLocalDateTimeToStatement(implicit
      meta: ParameterMetaData[LocalDateTime]
  ): ToStatement[LocalDateTime] = new ToStatement[LocalDateTime] {
    def set(s: PreparedStatement, i: Int, t: LocalDateTime): Unit =
      if (t == (null: LocalDateTime)) s.setNull(i, meta.jdbcType)
      else s.setTimestamp(i, new Timestamp(t.toDateTime.getMillis))
  }

  /**
   * Sets a local date on statement.
   */
  implicit def jodaLocalDateToStatement(implicit meta: ParameterMetaData[LocalDate]): ToStatement[LocalDate] =
    new ToStatement[LocalDate] {
      def set(s: PreparedStatement, i: Int, t: LocalDate): Unit =
        if (t == (null: LocalDate)) s.setNull(i, meta.jdbcType)
        else s.setTimestamp(i, new Timestamp(t.toDate.getTime))
    }

  /**
   * Sets joda-time Instant as statement parameter.
   * For `null` value, `setNull` with `TIMESTAMP` is called on statement.
   */
  implicit def jodaInstantToStatement(implicit meta: ParameterMetaData[Instant]): ToStatement[Instant] =
    new ToStatement[Instant] {
      def set(s: PreparedStatement, index: Int, instant: Instant): Unit =
        if (instant != (null: Instant)) {
          s.setTimestamp(index, new Timestamp(instant.getMillis))
        } else s.setNull(index, meta.jdbcType)
    }
}

object JodaToStatement extends JodaToStatement

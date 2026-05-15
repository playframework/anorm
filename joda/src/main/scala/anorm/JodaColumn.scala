/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import java.util.Date

trait JodaColumn {
  import org.joda.time.{ DateTime, Instant, LocalDate, LocalDateTime }
  import Column.{ className, nonNull, timestamp => Ts }

  /**
   * Parses column as Joda local date.
   * Time zone is the one of default JVM time zone
   * (see `org.joda.time.DateTimeZone.getDefault`).
   *
   * {{{
   * import org.joda.time.LocalDate
   * import anorm._, SqlParser.scalar
   * import anorm.JodaColumn._
   *
   * def ld(implicit con: java.sql.Connection): LocalDate =
   *   SQL("SELECT last_mod FROM tbl").as(scalar[LocalDate].single)
   * }}}
   */
  implicit val columnToJodaLocalDate: Column[LocalDate] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, _, _) = meta

      value match {
        case date: java.util.Date  => Right(new LocalDate(date.getTime))
        case time: Long            => Right(new LocalDate(time))
        case TimestampWrapper1(ts) => Ts(ts)(t => new LocalDate(t.getTime))
        case TimestampWrapper2(ts) => Ts(ts)(t => new LocalDate(t.getTime))
        case _                     =>
          Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Joda LocalDate for column $qualified"))
      }
    }

  /**
   * Parses column as Joda local date/time.
   * Time zone is the one of default JVM time zone
   * (see `org.joda.time.DateTimeZone.getDefault`).
   *
   * {{{
   * import org.joda.time.LocalDateTime
   * import anorm._, SqlParser._
   * import anorm.JodaColumn._
   *
   * def ldt(implicit con: java.sql.Connection): LocalDateTime =
   *   SQL("SELECT last_mod FROM tbl").as(scalar[LocalDateTime].single)
   * }}}
   */
  implicit val columnToJodaLocalDateTime: Column[LocalDateTime] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, _, _) = meta

      value match {
        case date: java.util.Date  => Right(new LocalDateTime(date.getTime))
        case time: Long            => Right(new LocalDateTime(time))
        case TimestampWrapper1(ts) => Ts(ts)(t => new LocalDateTime(t.getTime))
        case TimestampWrapper2(ts) => Ts(ts)(t => new LocalDateTime(t.getTime))
        case _                     =>
          Left(
            TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Joda LocalDateTime for column $qualified")
          )
      }
    }

  /**
   * Parses column as joda DateTime
   *
   * {{{
   * import org.joda.time.DateTime
   * import anorm._, SqlParser._
   * import anorm.JodaColumn._
   *
   * def dt(implicit con: java.sql.Connection): DateTime =
   *   SQL("SELECT last_mod FROM tbl").as(scalar[DateTime].single)
   * }}}
   */
  implicit val columnToJodaDateTime: Column[DateTime] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, _, _) = meta

      @SuppressWarnings(Array("AsInstanceOf"))
      def unsafe = value match {
        case date: Date            => Right(new DateTime(date.getTime))
        case time: Long            => Right(new DateTime(time))
        case TimestampWrapper1(ts) =>
          Option(ts).fold(Right(null.asInstanceOf[DateTime]))(t => Right(new DateTime(t.getTime)))

        case TimestampWrapper2(ts) =>
          Option(ts).fold(Right(null.asInstanceOf[DateTime]))(t => Right(new DateTime(t.getTime)))

        case _ =>
          Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to DateTime for column $qualified"))
      }

      unsafe
    }

  /**
   * Parses column as joda Instant
   *
   * {{{
   * import anorm._, SqlParser.scalar
   * import org.joda.time.Instant
   * import anorm.JodaColumn._
   *
   * def d(implicit con: java.sql.Connection): Instant =
   *   SQL("SELECT last_mod FROM tbl").as(scalar[Instant].single)
   * }}}
   */
  implicit val columnToJodaInstant: Column[Instant] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, _, _) = meta
      value match {
        case date: Date            => Right(new Instant(date.getTime))
        case time: Long            => Right(new Instant(time))
        case TimestampWrapper1(ts) => Ts(ts)(t => new Instant(t.getTime))
        case TimestampWrapper2(ts) => Ts(ts)(t => new Instant(t.getTime))
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Instant for column $qualified"))
      }
    }
}

object JodaColumn extends JodaColumn

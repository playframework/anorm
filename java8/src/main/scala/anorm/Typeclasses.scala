package anorm

import java.sql.{ PreparedStatement, Timestamp, Types }
import java.time.Instant

/** Provides Java8 specific typeclasses. */
object Java8 {
  /**
   * Sets a temporal instant on statement.
   *
   * {{{
   * import java.time.Instant
   *
   * SQL("SELECT * FROM Test WHERE time < {b}").on('b -> Instant.now)
   * }}}
   */
  implicit object instantToStatement extends ToStatement[Instant] {
    def set(s: PreparedStatement, i: Int, t: Instant): Unit =
      if (t == null) s.setNull(i, Types.TIMESTAMP)
      else s.setTimestamp(i, Timestamp from t)
  }

  /** Parameter metadata for Java8 instant */
  implicit object InstantParameterMetaData extends ParameterMetaData[Instant] {
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
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to Java8 Instant for column $qualified"))
      }
    }
}

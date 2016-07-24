/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package anorm

import java.io.{ ByteArrayInputStream, InputStream }
import java.math.{ BigDecimal => JBigDec, BigInteger }
import java.util.{ Date, UUID }
import java.sql.Timestamp

import scala.language.reflectiveCalls
import scala.util.{ Failure, Success => TrySuccess, Try }

import resource.managed

import scala.language.reflectiveCalls

/** Column mapping */
@annotation.implicitNotFound(
  "No column extractor found for the type ${A}: `anorm.Column[${A}]` required; See https://github.com/playframework/anorm/blob/master/docs/manual/working/scalaGuide/main/sql/ScalaAnorm.md#column-parsers")
trait Column[A] extends ((Any, MetaDataItem) => MayErr[SqlRequestError, A])

/** Column companion, providing default conversions. */
object Column extends JodaColumn with JavaTimeColumn {
  def apply[A](transformer: ((Any, MetaDataItem) => MayErr[SqlRequestError, A])): Column[A] = new Column[A] {

    def apply(value: Any, meta: MetaDataItem): MayErr[SqlRequestError, A] =
      transformer(value, meta)

  }

  @deprecated(message = "Use [[nonNull]]", since = "2.5.1")
  def nonNull1[A](transformer: ((Any, MetaDataItem) => Either[SqlRequestError, A])): Column[A] = nonNull[A](transformer)

  /**
   * Helper function to implement column conversion.
   *
   * @param transformer Function converting raw value of column
   * @tparam Output type
   */
  def nonNull[A](transformer: ((Any, MetaDataItem) => Either[SqlRequestError, A])): Column[A] = Column[A] {
    case (value, meta @ MetaDataItem(qualified, _, _)) =>
      MayErr(if (value != null) transformer(value, meta)
      else Left[SqlRequestError, A](
        UnexpectedNullableFound(qualified.toString)))

  }

  @inline private[anorm] def className(that: Any): String =
    if (that == null) "<null>" else that.getClass.getName

  private[anorm] def string[T](s: String)(f: String => T): Either[SqlRequestError, T] = Right(if (s == null) null.asInstanceOf[T] else f(s))

  implicit val columnToString: Column[String] =
    nonNull[String] { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case string: String => Right(string)
        case clob: java.sql.Clob => Right(clob.getSubString(1, clob.length.asInstanceOf[Int]))
        case StringWrapper2(s) => string(s)(identity)
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to String for column $qualified"))
      }
    }

  /**
   * Column conversion to bytes array.
   *
   * {{{
   * import anorm.SqlParser.scalar
   * import anorm.Column.columnToByteArray
   *
   * val bytes: Array[Byte] = SQL("SELECT bin FROM tbl").
   *   as(scalar[Array[Byte]].single)
   * }}}
   */
  implicit val columnToByteArray: Column[Array[Byte]] =
    nonNull[Array[Byte]] { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case bytes: Array[Byte] => Right(bytes)
        case stream: InputStream => streamBytes(stream)
        case string: String => Right(string.getBytes)
        case blob: java.sql.Blob => streamBytes(blob.getBinaryStream)
        case StringWrapper2(s) => string(s)(_.getBytes)
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to bytes array for column $qualified"))
      }
    }

  /**
   * Column conversion to character.
   *
   * {{{
   * import anorm.SqlParser.scalar
   * import anorm.Column.columnToChar
   *
   * val c: Char = SQL("SELECT char FROM tbl").as(scalar[Char].single)
   * }}}
   */
  implicit val columnToChar: Column[Char] = nonNull[Char] { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case string: String => Right(string.charAt(0))
      case clob: java.sql.Clob => Right(clob.getSubString(1, 1).charAt(0))
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Char for column $qualified"))
    }
  }

  implicit val columnToInt: Column[Int] = nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case bi: BigInteger => Right(bi.intValue)
      case bd: JBigDec => Right(bd.intValue)
      case l: Long => Right(l.toInt)
      case i: Int => Right(i)
      case s: Short => Right(s.toInt)
      case b: Byte => Right(b.toInt)
      case bool: Boolean => Right(if (!bool) 0 else 1)
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Int for column $qualified"))
    }
  }

  /**
   * Column conversion to bytes array.
   *
   * {{{
   * import anorm.SqlParser.scalar
   * import anorm.Column.columnToInputStream
   *
   * val bytes: InputStream = SQL("SELECT bin FROM tbl").
   *   as(scalar[InputStream].single)
   * }}}
   */
  implicit val columnToInputStream: Column[InputStream] =
    nonNull[InputStream] { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case bytes: Array[Byte] => Right(new ByteArrayInputStream(bytes))
        case stream: InputStream => Right(stream)
        case string: String => Right(new ByteArrayInputStream(string.getBytes))
        case blob: java.sql.Blob => Right(blob.getBinaryStream)
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to input stream for column $qualified"))
      }
    }

  implicit val columnToFloat: Column[Float] = nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case f: Float => Right(f)
      case bi: BigInteger => Right(bi.floatValue)
      case i: Int => Right(i.toFloat)
      case s: Short => Right(s.toFloat)
      case b: Byte => Right(b.toFloat)
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Float for column $qualified"))
    }
  }

  implicit val columnToDouble: Column[Double] = nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case bg: JBigDec => Right(bg.doubleValue)
      case d: Double => Right(d)
      case f: Float => Right(new JBigDec(f.toString).doubleValue)
      case bi: BigInteger => Right(bi.doubleValue)
      case i: Int => Right(i.toDouble)
      case s: Short => Right(s.toDouble)
      case b: Byte => Right(b.toDouble)
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Double for column $qualified"))
    }
  }

  implicit val columnToShort: Column[Short] = nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case b: Byte => Right(b.toShort)
      case s: Short => Right(s)
      case bool: Boolean => Right(if (!bool) 0.toShort else 1.toShort)
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Short for column $qualified"))
    }
  }

  implicit val columnToByte: Column[Byte] = nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case b: Byte => Right(b)
      case s: Short => Right(s.toByte)
      case bool: Boolean => Right(if (!bool) 0.toByte else 1.toByte)
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Byte for column $qualified"))
    }
  }

  implicit val columnToBoolean: Column[Boolean] = nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case bool: Boolean => Right(bool)
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Boolean for column $qualified"))
    }
  }

  private[anorm] def timestamp[T](ts: Timestamp)(f: Timestamp => T): Either[SqlRequestError, T] = Right(if (ts == null) null.asInstanceOf[T] else f(ts))

  implicit val columnToLong: Column[Long] = nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case bi: BigInteger => Right(bi.longValue)
      case bd: JBigDec => Right(bd.longValue)
      case int: Int => Right(int: Long)
      case long: Long => Right(long)
      case s: Short => Right(s.toLong)
      case b: Byte => Right(b.toLong)
      case bool: Boolean => Right(if (!bool) 0L else 1L)
      case date: Date => Right(date.getTime)
      case TimestampWrapper1(ts) => timestamp(ts)(_.getTime)
      case TimestampWrapper2(ts) => timestamp(ts)(_.getTime)
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Long for column $qualified"))
    }
  }

  // Used to convert Java or Scala big integer
  private def anyToBigInteger(value: Any, meta: MetaDataItem): Either[SqlRequestError, BigInteger] = {
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case bi: BigInteger => Right(bi)
      case bd: JBigDec => Right(bd.toBigInteger)
      case long: Long => Right(BigInteger.valueOf(long))
      case int: Int => Right(BigInteger.valueOf(int))
      case s: Short => Right(BigInteger.valueOf(s))
      case b: Byte => Right(BigInteger.valueOf(b))
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to BigInteger for column $qualified"))
    }
  }

  /**
   * Column conversion to Java big integer.
   *
   * {{{
   * import anorm.SqlParser.scalar
   * import anorm.Column.columnToScalaBigInteger
   *
   * val c: BigInteger =
   *   SQL("SELECT COUNT(*) FROM tbl").as(scalar[BigInteger].single)
   * }}}
   */
  implicit val columnToBigInteger: Column[BigInteger] = nonNull(anyToBigInteger)

  /**
   * Column conversion to big integer.
   *
   * {{{
   * import anorm.SqlParser.scalar
   * import anorm.Column.columnToBigInt
   *
   * val c: BigInt =
   *   SQL("SELECT COUNT(*) FROM tbl").as(scalar[BigInt].single)
   * }}}
   */
  implicit val columnToBigInt: Column[BigInt] =
    nonNull((value, meta) => anyToBigInteger(value, meta).right.map(BigInt(_)))

  implicit val columnToUUID: Column[UUID] = nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case d: UUID => Right(d)
      case s: String => Try { UUID.fromString(s) } match {
        case TrySuccess(v) => Right(v)
        case Failure(ex) => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to UUID for column $qualified"))
      }
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to UUID for column $qualified"))
    }
  }

  // Used to convert Java or Scala big decimal
  private def anyToBigDecimal(value: Any, meta: MetaDataItem): Either[SqlRequestError, JBigDec] = {
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case bd: JBigDec => Right(bd)
      case bi: BigInteger => Right(new JBigDec(bi))
      case d: Double => Right(JBigDec.valueOf(d))
      case f: Float => Right(JBigDec.valueOf(f))
      case l: Long => Right(JBigDec.valueOf(l))
      case i: Int => Right(JBigDec.valueOf(i))
      case s: Short => Right(JBigDec.valueOf(s))
      case b: Byte => Right(JBigDec.valueOf(b))
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to BigDecimal for column $qualified"))
    }
  }

  /**
   * Column conversion to Java big decimal.
   *
   * {{{
   * import java.math.{ BigDecimal => JBigDecimal }
   * import anorm.SqlParser.scalar
   * import anorm.Column.columnToJavaBigDecimal
   *
   * val c: JBigDecimal =
   *   SQL("SELECT COUNT(*) FROM tbl").as(scalar[JBigDecimal].single)
   * }}}
   */
  implicit val columnToJavaBigDecimal: Column[JBigDec] =
    nonNull(anyToBigDecimal)

  /**
   * Column conversion to big decimal.
   *
   * {{{
   * import anorm.SqlParser.scalar
   * import anorm.Column.columnToScalaBigDecimal
   *
   * val c: BigDecimal =
   *   SQL("SELECT COUNT(*) FROM tbl").as(scalar[BigDecimal].single)
   * }}}
   */
  implicit val columnToScalaBigDecimal: Column[BigDecimal] =
    nonNull((value, meta) =>
      anyToBigDecimal(value, meta).right.map(BigDecimal(_)))

  /**
   * Parses column as Java Date.
   * Time zone offset is the one of default JVM time zone
   * (see [[java.util.TimeZone#getDefault TimeZone.getDefault]]).
   *
   * {{{
   * import java.util.Date
   *
   * val d: Date = SQL("SELECT last_mod FROM tbl").as(scalar[Date].single)
   * }}}
   */
  implicit val columnToDate: Column[Date] = nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case date: Date => Right(date)
      case time: Long => Right(new Date(time))
      case TimestampWrapper1(ts) => timestamp(ts)(t => new Date(t.getTime))
      case TimestampWrapper2(ts) => timestamp(ts)(t => new Date(t.getTime))
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Date for column $qualified"))
    }
  }

  implicit def columnToOption[T](implicit transformer: Column[T]): Column[Option[T]] = Column { (value, meta) =>
    if (value != null) transformer(value, meta).map(Some(_))
    else MayErr(Right[SqlRequestError, Option[T]](None))
  }

  /**
   * Parses column as array.
   *
   * val a: Array[String] =
   *   SQL"SELECT str_arr FROM tbl".as(scalar[Array[String]])
   * }}}
   */
  implicit def columnToArray[T](implicit transformer: Column[T], t: scala.reflect.ClassTag[T]): Column[Array[T]] = Column.nonNull[Array[T]] { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta

    @inline def typeNotMatch(value: Any, target: String, cause: Any) = TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to $target for column $qualified: $cause")

    @annotation.tailrec
    def transf(a: Array[_], p: Array[T]): Either[SqlRequestError, Array[T]] =
      a.headOption match {
        case Some(r) => transformer(r, meta).toEither match {
          case Right(v) => transf(a.tail, p :+ v)
          case Left(cause) => Left(typeNotMatch(value, "array", cause))
        }
        case _ => Right(p)
      }

    @annotation.tailrec
    def jiter(i: java.util.Iterator[_], p: Array[T]): Either[SqlRequestError, Array[T]] = if (!i.hasNext) Right(p)
    else transformer(i.next, meta).toEither match {
      case Right(v) => jiter(i, p :+ v)
      case Left(cause) => Left(typeNotMatch(value, "list", cause))
    }

    value match {
      case sql: java.sql.Array => try {
        transf(sql.getArray.asInstanceOf[Array[_]], Array.empty[T])
      } catch {
        case cause: Throwable => Left(typeNotMatch(value, "array", cause))
      }

      case arr: Array[_] => try {
        transf(arr, Array.empty[T])
      } catch {
        case cause: Throwable => Left(typeNotMatch(value, "list", cause))
      }

      case it: java.lang.Iterable[_] => try {
        jiter(it.iterator, Array.empty[T])
      } catch {
        case cause: Throwable => Left(typeNotMatch(value, "list", cause))
      }

      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to array for column $qualified"))
    }
  }

  /**
   * Parses column as list.
   *
   * val a: List[String] =
   *   SQL"SELECT str_arr FROM tbl".as(scalar[List[String]])
   * }}}
   */
  implicit def columnToList[T](implicit transformer: Column[T], t: scala.reflect.ClassTag[T]): Column[List[T]] = Column.nonNull[List[T]] { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta

    @inline def typeNotMatch(value: Any, target: String, cause: Any) = TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to $target for column $qualified: $cause")

    @annotation.tailrec
    def transf(a: Array[_], p: List[T]): Either[SqlRequestError, List[T]] =
      a.headOption match {
        case Some(r) => transformer(r, meta).toEither match {
          case Right(v) => transf(a.tail, p :+ v)
          case Left(cause) => Left(typeNotMatch(value, "list", cause))
        }
        case _ => Right(p)
      }

    @annotation.tailrec
    def jiter(i: java.util.Iterator[_], p: List[T]): Either[SqlRequestError, List[T]] = if (!i.hasNext) Right(p)
    else transformer(i.next, meta).toEither match {
      case Right(v) => jiter(i, p :+ v)
      case Left(cause) => Left(typeNotMatch(value, "list", cause))
    }

    value match {
      case sql: java.sql.Array => try {
        transf(sql.getArray.asInstanceOf[Array[_]], Nil)
      } catch {
        case cause: Throwable => Left(typeNotMatch(value, "list", cause))
      }

      case arr: Array[_] => try {
        transf(arr, Nil)
      } catch {
        case cause: Throwable => Left(typeNotMatch(value, "list", cause))
      }

      case it: java.lang.Iterable[_] => try {
        jiter(it.iterator, Nil)
      } catch {
        case cause: Throwable => Left(typeNotMatch(value, "list", cause))
      }

      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to list for column $qualified"))
    }
  }

  @inline private def streamBytes(in: InputStream): Either[SqlRequestError, Array[Byte]] = managed(in).acquireFor(streamToBytes(_)).fold({ errs =>
    Left(SqlMappingError(errs.headOption.
      fold("Fails to read binary stream")(_.getMessage)))
  }, Right(_))

  @annotation.tailrec
  private def streamToBytes(in: InputStream, bytes: Array[Byte] = Array(), buffer: Array[Byte] = Array.ofDim(1024)): Array[Byte] = {
    val count = in.read(buffer)

    if (count == -1) bytes
    else streamToBytes(in, bytes ++ buffer.take(count), buffer)
  }
}

sealed trait JodaColumn {
  import org.joda.time.{ DateTime, LocalDate, LocalDateTime, Instant }
  import Column.{ nonNull, className, timestamp => Ts }

  /**
   * Parses column as Joda local date.
   * Time zone is the one of default JVM time zone
   * (see [[org.joda.time.DateTimeZone#getDefault DateTimeZone.getDefault]]).
   *
   * {{{
   * import org.joda.time.LocalDate
   *
   * val i: LocalDate = SQL("SELECT last_mod FROM tbl").
   *   as(scalar[LocalDate].single)
   * }}}
   */
  implicit val columnToJodaLocalDate: Column[LocalDate] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta

      value match {
        case date: java.util.Date => Right(new LocalDate(date.getTime))
        case time: Long => Right(new LocalDate(time))
        case TimestampWrapper1(ts) => Ts(ts)(t => new LocalDate(t.getTime))
        case TimestampWrapper2(ts) => Ts(ts)(t => new LocalDate(t.getTime))
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Joda LocalDate for column $qualified"))
      }
    }

  /**
   * Parses column as Joda local date/time.
   * Time zone is the one of default JVM time zone
   * (see [[org.joda.time.DateTimeZone#getDefault DateTimeZone.getDefault]]).
   *
   * {{{
   * import org.joda.time.LocalDateTime
   *
   * val i: LocalDateTime = SQL("SELECT last_mod FROM tbl").
   *   as(scalar[LocalDateTime].single)
   * }}}
   */
  implicit val columnToJodaLocalDateTime: Column[LocalDateTime] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta

      value match {
        case date: java.util.Date => Right(new LocalDateTime(date.getTime))
        case time: Long => Right(new LocalDateTime(time))
        case TimestampWrapper1(ts) => Ts(ts)(t => new LocalDateTime(t.getTime))
        case TimestampWrapper2(ts) => Ts(ts)(t => new LocalDateTime(t.getTime))
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Joda LocalDateTime for column $qualified"))
      }
    }

  /**
   * Parses column as joda DateTime
   *
   * {{{
   * import org.joda.time.DateTime
   *
   * val d: Date = SQL("SELECT last_mod FROM tbl").as(scalar[DateTime].single)
   * }}}
   */
  implicit val columnToJodaDateTime: Column[DateTime] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case date: Date => Right(new DateTime(date.getTime))
        case time: Long => Right(new DateTime(time))
        case TimestampWrapper1(ts) =>
          Option(ts).fold(Right(null.asInstanceOf[DateTime]))(t =>
            Right(new DateTime(t.getTime)))
        case TimestampWrapper2(ts) =>
          Option(ts).fold(Right(null.asInstanceOf[DateTime]))(t =>
            Right(new DateTime(t.getTime)))

        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to DateTime for column $qualified"))
      }
    }

  /**
   * Parses column as joda Instant
   *
   * {{{
   * import org.joda.time.Instant
   *
   * val d: Date = SQL("SELECT last_mod FROM tbl").as(scalar[Instant].single)
   * }}}
   */
  implicit val columnToJodaInstant: Column[Instant] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case date: Date => Right(new Instant(date.getTime))
        case time: Long => Right(new Instant(time))
        case TimestampWrapper1(ts) => Ts(ts)(t => new Instant(t.getTime))
        case TimestampWrapper2(ts) => Ts(ts)(t => new Instant(t.getTime))
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Instant for column $qualified"))
      }
    }
}

sealed trait JavaTimeColumn {
  import java.time.{ ZonedDateTime, ZoneId, LocalDate, LocalDateTime, Instant }
  import Column.{ nonNull, className, timestamp => Ts }

  /**
   * Parses column as Java8 instant.
   * Time zone offset is the one of default JVM time zone
   * (see [[java.time.ZoneId#systemDefault ZoneId.systemDefault]]).
   *
   * {{{
   * import java.time.Instant
   * import anorm.Java8._
   *
   * val i: Instant = SQL("SELECT last_mod FROM tbl").as(scalar[Instant].single)
   * }}}
   */
  implicit val columnToInstant: Column[Instant] = nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case date: java.util.Date => Right(Instant ofEpochMilli date.getTime)
      case time: Long => Right(Instant ofEpochMilli time)
      case TimestampWrapper1(ts) => Ts(ts)(Instant ofEpochMilli _.getTime)
      case TimestampWrapper2(ts) => Ts(ts)(Instant ofEpochMilli _.getTime)
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Java8 Instant for column $qualified"))
    }
  }

  /**
   * Parses column as Java8 local date/time.
   * Time zone offset is the one of default JVM time zone
   * (see [[java.time.ZoneId#systemDefault ZoneId.systemDefault]]).
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

    nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case date: java.util.Date => Right(dateTime(date.getTime))
        case time: Long => Right(dateTime(time))
        case TimestampWrapper1(ts) => Ts(ts)(t => dateTime(t.getTime))
        case TimestampWrapper2(ts) => Ts(ts)(t => dateTime(t.getTime))
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Java8 LocalDateTime for column $qualified"))
      }
    }
  }

  /**
   * Parses column as Java8 local date.
   * Time zone offset is the one of default JVM time zone
   * (see [[java.time.ZoneId#systemDefault ZoneId.systemDefault]]).
   *
   * {{{
   * import java.time.LocalDateTime
   * import anorm.Java8._
   *
   * val i: LocalDateTime = SQL("SELECT last_mod FROM tbl").
   *   as(scalar[LocalDateTime].single)
   * }}}
   */
  implicit val columnToLocalDate: Column[LocalDate] = {
    @inline def localDate(ts: Long) = LocalDateTime.ofInstant(
      Instant.ofEpochMilli(ts), ZoneId.systemDefault).toLocalDate

    nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case date: java.util.Date => Right(localDate(date.getTime))
        case time: Long => Right(localDate(time))
        case TimestampWrapper1(ts) => Ts(ts)(t => localDate(t.getTime))
        case TimestampWrapper2(ts) => Ts(ts)(t => localDate(t.getTime))
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Java8 LocalDate for column $qualified"))
      }
    }
  }

  /**
   * Parses column as Java8 zoned date/time.
   * Time zone offset is the one of default JVM time zone
   * (see [[java.time.ZoneId#systemDefault ZoneId.systemDefault]]).
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

    nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case date: java.util.Date => Right(dateTime(date.getTime))
        case time: Long => Right(dateTime(time))
        case TimestampWrapper1(ts) => Ts(ts)(t => dateTime(t.getTime))
        case TimestampWrapper2(ts) => Ts(ts)(t => dateTime(t.getTime))
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Java8 ZonedDateTime for column $qualified"))
      }
    }
  }
}

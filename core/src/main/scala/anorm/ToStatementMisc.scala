package anorm

import java.lang.{
  Boolean => JBool,
  Byte => JByte,
  Double => JDouble,
  Float => JFloat,
  Long => JLong,
  Short => JShort
}

import java.util.{ UUID => JUUID }

import java.net.{ URI, URL }

import java.math.{ BigDecimal => JBigDec, BigInteger }

import java.sql.{ PreparedStatement, Timestamp }

private[anorm] trait ToStatementBase[A] { self =>

  /**
   * Sets value |v| on statement |s| at specified |index|.
   */
  def set(s: PreparedStatement, index: Int, v: A): Unit
}

/*
 * Provided instances of `ToStatement` with the lower priority.
 * Actually it makes sure that `byteArrayToStatement`
 * (defined in `ToStatementPriority1`) is resolved before `arrayToParameter`.
 */
sealed trait ToStatementPriority0 {
  import scala.collection.immutable.SortedSet
  import java.io.{ InputStream, Reader }
  import scala.language.reflectiveCalls

  /**
   * Sets a binary stream as parameter on statement.
   * For `null` value, `setNull` with `LONGVARBINARY` is called on statement.
   *
   * {{{
   * SQL("INSERT INTO Table(bin) VALUES {b}").on("b" -> inputStream)
   * }}}
   */
  implicit def binaryStreamToStatement[S <: InputStream]: ToStatement[S] =
    new ToStatement[S] {
      val jdbcType = implicitly[ParameterMetaData[InputStream]].jdbcType
      def set(s: PreparedStatement, i: Int, bin: S) =
        if (bin == (null: InputStream)) s.setNull(i, jdbcType)
        else s.setBinaryStream(i, bin)
    }

  /**
   * Sets a blob as parameter on statement.
   * For `null` value, `setNull` with `BLOB` is called on statement.
   *
   * {{{
   * val blob = con.createBlob()
   * blob.setBytes(1, byteArray)
   * SQL("INSERT INTO Table(bin) VALUES {b}").on("b" -> blob)
   * }}}
   */
  implicit def blobToStatement[B <: java.sql.Blob]: ToStatement[B] =
    new ToStatement[B] {
      val jdbcType = implicitly[ParameterMetaData[java.sql.Blob]].jdbcType
      def set(s: PreparedStatement, i: Int, blob: B) =
        if (blob == (null: java.sql.Blob)) s.setNull(i, jdbcType)
        else s.setBlob(i, blob)
    }

  /**
   * Sets a character stream as parameter on statement.
   * For `null` value, `setNull` with `VARCHAR` is called on statement.
   *
   * {{{
   * SQL("INSERT INTO Table(chars) VALUES {c}").on("c" -> reader)
   * }}}
   */
  implicit def characterStreamToStatement[R <: Reader]: ToStatement[R] =
    new ToStatement[R] {
      val jdbcType = implicitly[ParameterMetaData[Reader]].jdbcType
      def set(s: PreparedStatement, i: Int, chars: R) =
        if (chars == (null: Reader)) s.setNull(i, jdbcType)
        else s.setCharacterStream(i, chars)
    }

  /**
   * Sets boolean value on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE enabled = {b}").on('b -> true)
   * }}}
   */
  implicit object booleanToStatement extends ToStatement[Boolean] {
    def set(s: PreparedStatement, i: Int, b: Boolean): Unit = s.setBoolean(i, b)
  }

  /**
   * Sets Java Boolean object on statement.
   * For `null` value, `setNull` with `BOOLEAN` is called on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE enabled = {b}").
   *   on('b -> java.lang.Boolean.TRUE)
   * }}}
   */
  implicit object javaBooleanToStatement extends ToStatement[JBool] {
    val jdbcType = implicitly[ParameterMetaData[JBool]].jdbcType
    def set(s: PreparedStatement, i: Int, b: JBool): Unit =
      if (b != (null: JBool)) s.setBoolean(i, b) else s.setNull(i, jdbcType)
  }

  /**
   * Sets byte value on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE flag = {b}").on('b -> 1.toByte)
   * }}}
   */
  implicit object byteToStatement extends ToStatement[Byte] {
    def set(s: PreparedStatement, i: Int, b: Byte): Unit = s.setByte(i, b)
  }

  /**
   * Sets Java Byte object on statement.
   * For `null` value, `setNull` with `TINYINT` is called on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE flag = {b}").on('b -> new java.lang.Byte(1))
   * }}}
   */
  implicit object javaByteToStatement extends ToStatement[JByte] {
    val jdbcType = implicitly[ParameterMetaData[JByte]].jdbcType
    def set(s: PreparedStatement, i: Int, b: JByte): Unit =
      if (b != (null: JByte)) s.setByte(i, b) else s.setNull(i, jdbcType)
  }

  /**
   * Sets double value on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE flag = {b}").on('b -> 1d)
   * }}}
   */
  implicit object doubleToStatement extends ToStatement[Double] {
    def set(s: PreparedStatement, i: Int, d: Double): Unit = s.setDouble(i, d)
  }

  /**
   * Sets Java Double object on statement.
   * For `null` value, `setNull` with `DOUBLE` is called on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE flag = {b}").
   *   on('b -> new java.lang.Double(1d))
   * }}}
   */
  implicit object javaDoubleToStatement extends ToStatement[JDouble] {
    val jdbcType = implicitly[ParameterMetaData[JDouble]].jdbcType
    def set(s: PreparedStatement, i: Int, d: JDouble): Unit =
      if (d != (null: JDouble)) s.setDouble(i, d) else s.setNull(i, jdbcType)
  }

  /**
   * Sets float value on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE flag = {b}").on('b -> 1f)
   * }}}
   */
  implicit object floatToStatement extends ToStatement[Float] {
    def set(s: PreparedStatement, i: Int, f: Float): Unit = s.setFloat(i, f)
  }

  /**
   * Sets Java Float object on statement.
   * For `null` value, `setNull` with `FLOAT` is called on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE flag = {b}").
   *   on('b -> new java.lang.Float(1f))
   * }}}
   */
  implicit object javaFloatToStatement extends ToStatement[JFloat] {
    val jdbcType = implicitly[ParameterMetaData[JFloat]].jdbcType
    def set(s: PreparedStatement, i: Int, f: JFloat): Unit =
      if (f != (null: JFloat)) s.setFloat(i, f) else s.setNull(i, jdbcType)
  }

  /**
   * Sets long value on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE flag = {b}").on('b -> 1l)
   * }}}
   */
  implicit object longToStatement extends ToStatement[Long] {
    def set(s: PreparedStatement, i: Int, l: Long): Unit = s.setLong(i, l)
  }

  /**
   * Sets Java Long object on statement.
   * For `null` value, `setNull` with `BIGINT` is called on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE flag = {b}").
   *   on('b -> new java.lang.Long(1l))
   * }}}
   */
  implicit object javaLongToStatement extends ToStatement[JLong] {
    val jdbcType = implicitly[ParameterMetaData[JLong]].jdbcType
    def set(s: PreparedStatement, i: Int, l: JLong): Unit =
      if (l != (null: JLong)) s.setLong(i, l) else s.setNull(i, jdbcType)
  }

  /**
   * Sets integer value on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE flag = {b}").on('b -> 1)
   * }}}
   */
  implicit object intToStatement extends ToStatement[Int] {
    def set(s: PreparedStatement, i: Int, v: Int): Unit = s.setInt(i, v)
  }

  /**
   * Sets Java Integer object on statement.
   * For `null` value, `setNull` with `INTEGER` is called on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE flag = {b}").
   *   on('b -> new java.lang.Integer(1))
   * }}}
   */
  implicit object integerToStatement extends ToStatement[Integer] {
    val jdbcType = implicitly[ParameterMetaData[Integer]].jdbcType
    def set(s: PreparedStatement, i: Int, v: Integer): Unit =
      if (v != (null: Integer)) s.setInt(i, v) else s.setNull(i, jdbcType)
  }

  /**
   * Sets short value on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE flag = {b}").on('b -> 1.toShort)
   * }}}
   */
  implicit object shortToStatement extends ToStatement[Short] {
    def set(s: PreparedStatement, i: Int, v: Short): Unit = s.setShort(i, v)
  }

  /**
   * Sets Java Short object on statement.
   * For `null` value, `setNull` with `SMALLINT` is called on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE flag = {b}").
   *   on('b -> new java.lang.Short(1.toShort))
   * }}}
   */
  implicit object javaShortToStatement extends ToStatement[JShort] {
    val jdbcType = implicitly[ParameterMetaData[JShort]].jdbcType
    def set(s: PreparedStatement, i: Int, v: JShort): Unit =
      if (v != (null: JShort)) s.setShort(i, v) else s.setNull(i, jdbcType)
  }

  /**
   * Sets Java Character as parameter value.
   * For `null` character, `setNull` with `VARCHAR` is called on statement.
   *
   * {{{
   * SQL("SELECT * FROM tbl WHERE flag = {c}").
   *   on("c" -> new java.lang.Character('f'))
   * }}}
   */
  implicit object characterToStatement extends ToStatement[Character] {
    val jdbcType = implicitly[ParameterMetaData[Character]].jdbcType
    def set(s: PreparedStatement, i: Int, ch: Character) =
      if (ch != (null: Character)) s.setString(i, ch.toString)
      else s.setNull(i, jdbcType)
  }

  /**
   * Sets character as parameter value.
   *
   * {{{
   * SQL("SELECT * FROM tbl WHERE flag = {c}").on("c" -> 'f')
   * }}}
   */
  implicit object charToStatement extends ToStatement[Char] {
    def set(s: PreparedStatement, i: Int, ch: Char): Unit =
      s.setString(i, ch.toString)
  }

  /**
   * Sets string as parameter value.
   * Value `null` is accepted.
   *
   * {{{
   * SQL("SELECT * FROM tbl WHERE name = {n}").on("n" -> "str")
   * }}}
   */
  implicit object stringToStatement extends ToStatement[String] {
    def set(s: PreparedStatement, i: Int, str: String) = s.setString(i, str)
  }

  /**
   * Sets null for None value.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE category = {c}")
   *   .on('c -> None)
   * }}}
   */
  @deprecated(
    "Parameter value should be passed using `Option.empty[T]`",
    since = "2.3.7")
  implicit object noneToStatement extends ToStatement[None.type] {
    def set(s: PreparedStatement, i: Int, n: None.type) =
      s.setObject(i, null: Object)
  }

  /**
   * Sets not empty optional A inferred as Some[A].
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE category = {c}").on('c -> Some("cat"))
   * }}}
   */
  implicit def someToStatement[A](implicit c: ToStatement[A]) =
    new ToStatement[Some[A]] with NotNullGuard {
      def set(s: PreparedStatement, index: Int, v: Some[A]): Unit =
        c.set(s, index, v.get)
    }

  /**
   * Sets optional A inferred as Option[A].
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE category = {c}").on('c -> Option("cat"))
   * SQL"SELECT * FROM Test WHERE nullable_int = \\${Option.empty[Int]}"
   * }}}
   */
  implicit def optionToStatement[A](
    implicit
    c: ToStatement[A],
    meta: ParameterMetaData[A]) = new ToStatement[Option[A]] with NotNullGuard {
    def set(s: PreparedStatement, index: Int, o: Option[A]) = {
      o.fold[Unit](s.setNull(index, meta.jdbcType))(c.set(s, index, _))
    }
  }

  /**
   * Sets Java big integer on statement.
   * For `null` value, `setNull` with `NUMERIC` is called on statement.
   *
   * {{{
   * SQL("UPDATE tbl SET max = {m}").on('m -> new BigInteger(15))
   * }}}
   */
  implicit object javaBigIntegerToStatement extends ToStatement[BigInteger] {
    val jdbcType = implicitly[ParameterMetaData[BigInteger]].jdbcType
    def set(s: PreparedStatement, index: Int, v: BigInteger): Unit =
      if (v != (null: BigInteger)) s.setBigDecimal(index, new JBigDec(v))
      else s.setNull(index, jdbcType)
  }

  /**
   * Sets big integer on statement.
   * For `null` value, `setNull` with `NUMERIC` is called on statement.
   *
   * {{{
   * SQL("UPDATE tbl SET max = {m}").on('m -> BigInt(15))
   * }}}
   */
  implicit object scalaBigIntegerToStatement extends ToStatement[BigInt] {
    val jdbcType = implicitly[ParameterMetaData[BigInt]].jdbcType
    def set(s: PreparedStatement, index: Int, v: BigInt): Unit =
      if (v != (null: BigInt)) s.setBigDecimal(index, new JBigDec(v.bigInteger))
      else s.setNull(index, jdbcType)
  }

  /**
   * Sets Java big decimal on statement.
   * Value `null` is accepted.
   *
   * {{{
   * SQL("UPDATE tbl SET max = {m}").on('m -> new java.math.BigDecimal(10.02f))
   * }}}
   */
  implicit object javaBigDecimalToStatement extends ToStatement[JBigDec] {
    def set(s: PreparedStatement, index: Int, v: JBigDec): Unit =
      s.setBigDecimal(index, v)
  }

  /**
   * Sets big decimal on statement.
   * For `null` value, `setNull` with `DECIMAL` is called on statement.
   *
   * {{{
   * SQL("UPDATE tbl SET max = {m}").on('m -> BigDecimal(10.02f))
   * }}}
   */
  implicit object scalaBigDecimalToStatement extends ToStatement[BigDecimal] {
    val jdbcType = implicitly[ParameterMetaData[BigDecimal]].jdbcType
    def set(s: PreparedStatement, index: Int, v: BigDecimal): Unit =
      if (v != (null: BigDecimal)) s.setBigDecimal(index, v.bigDecimal)
      else s.setNull(index, jdbcType)
  }

  /**
   * Sets timestamp as statement parameter.
   * Value `null` is accepted.
   *
   * {{{
   * SQL("UPDATE tbl SET modified = {ts}").
   *   on('ts -> new Timestamp(date.getTime))
   * }}}
   */
  implicit object timestampToStatement extends ToStatement[Timestamp] {
    def set(s: PreparedStatement, index: Int, ts: Timestamp): Unit =
      s.setTimestamp(index, ts)
  }

  /**
   * Sets date as statement parameter.
   * For `null` value, `setNull` with `TIMESTAMP` is called on statement.
   *
   * {{{
   * SQL("UPDATE tbl SET modified = {d}").on('d -> new Date())
   * }}}
   */
  implicit object dateToStatement extends ToStatement[java.util.Date] {
    val jdbcType = implicitly[ParameterMetaData[java.util.Date]].jdbcType
    def set(s: PreparedStatement, index: Int, date: java.util.Date): Unit =
      if (date != (null: java.util.Date)) {
        s.setTimestamp(index, new Timestamp(date.getTime))
      } else s.setNull(index, jdbcType)
  }

  /**
   * Sets a wrapped timestamp as statement parameter.
   * For `null` value, `setNull` with `TIMESTAMP` is called on statement.
   *
   * {{{
   * val wrapper = new {
   *   // Any value with a `.getTimestamp`
   *   val getTimestamp = new java.sql.Timestamp(123L)
   * }
   *
   * SQL("UPDATE tbl SET modified = {ts}").on('ts -> wrapper)
   * }}}
   */
  implicit def timestampWrapper1ToStatement[T <: TimestampWrapper1]: ToStatement[T] = new ToStatement[T] {
    def set(s: PreparedStatement, index: Int, tsw: T): Unit =
      if (tsw != (null: TimestampWrapper1) &&
        tsw.getTimestamp != (null: TimestampWrapper1)) {
        s.setTimestamp(index, tsw.getTimestamp)
      } else s.setNull(index, timestampWrapper1JdbcType)
  }
  private val timestampWrapper1JdbcType =
    implicitly[ParameterMetaData[TimestampWrapper1]].jdbcType

  /**
   * Sets UUID as statement parameter.
   * For `null` value, `setNull` with `VARCHAR` is called on statement.
   *
   * {{{
   * SQL("INSERT INTO lang_tbl(id, name) VALUE ({i}, {n})").
   *   on("i" -> JUUID.randomUUID(), "n" -> "lang")
   * }}}
   */
  implicit object uuidToStatement extends ToStatement[JUUID] {
    val jdbcType = implicitly[ParameterMetaData[JUUID]].jdbcType
    def set(s: PreparedStatement, index: Int, id: JUUID): Unit =
      if (id != (null: JUUID)) s.setString(index, id.toString)
      else s.setNull(index, jdbcType)
  }

  /**
   * Sets URI as statement parameter.
   * For `null` value, `setNull` with `VARCHAR` is called on statement.
   *
   * {{{
   * SQL("INSERT INTO lang_tbl(id, name) VALUE ({i}, {n})").
   *   on("i" -> new URI("https://github.com/playframework/"), "n" -> "lang")
   * }}}
   */
  implicit object uriToStatement extends ToStatement[URI] {
    val jdbcType = implicitly[ParameterMetaData[URI]].jdbcType
    def set(s: PreparedStatement, index: Int, uri: URI): Unit =
      if (uri != (null: URI)) s.setString(index, uri.toString)
      else s.setNull(index, jdbcType)
  }

  /**
   * Sets URL as statement parameter.
   * For `null` value, `setNull` with `VARCHAR` is called on statement.
   *
   * {{{
   * SQL("INSERT INTO lang_tbl(id, name) VALUE ({i}, {n})").
   *   on("i" -> new URL("https://github.com/playframework/"), "n" -> "lang")
   * }}}
   */
  implicit object urlToStatement extends ToStatement[URL] {
    val jdbcType = implicitly[ParameterMetaData[URL]].jdbcType
    def set(s: PreparedStatement, index: Int, url: URL): Unit =
      if (url != (null: URL)) s.setString(index, url.toString)
      else s.setNull(index, jdbcType)
  }

  /**
   * Sets opaque value as statement parameter.
   * UNSAFE: It's set using `java.sql.PreparedStatement.setObject`.
   *
   * {{{
   * SQL("EXEC indexed_at {d}").on('d -> anorm.Object(new java.util.Date()))
   * }}}
   */
  implicit object objectToStatement extends ToStatement[anorm.Object] {
    def set(s: PreparedStatement, index: Int, o: anorm.Object): Unit =
      s.setObject(index, o.value)
  }

  /**
   * Sets multi-value parameter from list on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE cat IN ({categories})").
   *   on('categories -> List(1, 3, 4)
   * }}}
   */
  implicit def listToStatement[A](implicit c: ToStatement[A]): ToStatement[List[A]] = traversableToStatement[A, List[A]]

  /**
   * Sets multi-value parameter from sequence on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE cat IN ({categories})").
   *   on('categories -> Seq("a", "b", "c")
   * }}}
   */
  implicit def seqToStatement[A](implicit c: ToStatement[A]): ToStatement[Seq[A]] = traversableToStatement[A, Seq[A]]

  /**
   * Sets multi-value parameter from set on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE cat IN ({categories})").
   *   on('categories -> Set(1, 3, 4)
   * }}}
   */
  implicit def setToStatement[A](implicit c: ToStatement[A]): ToStatement[Set[A]] = traversableToStatement[A, Set[A]]

  /**
   * Sets multi-value parameter from sorted set on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE cat IN ({categories})").
   *   on('categories -> SortedSet("a", "b", "c")
   * }}}
   */
  implicit def sortedSetToStatement[A](implicit c: ToStatement[A]): ToStatement[SortedSet[A]] = traversableToStatement[A, SortedSet[A]]

  /**
   * Sets multi-value parameter from stream on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE cat IN ({categories})").
   *   on('categories -> Stream(1, 3, 4)
   * }}}
   */
  implicit def streamToStatement[A](implicit c: ToStatement[A]): ToStatement[Compat.LazyLst[A]] = traversableToStatement[A, Compat.LazyLst[A]]

  /**
   * Sets multi-value parameter from vector on statement.
   *
   * {{{
   * SQL("SELECT * FROM Test WHERE cat IN ({categories})").
   *   on('categories -> Vector("a", "b", "c")
   * }}}
   */
  implicit def vectorToStatement[A](implicit c: ToStatement[A]): ToStatement[Vector[A]] = traversableToStatement[A, Vector[A]]

  /**
   * Sets multi-value parameter on statement, with custom formatting
   * (using [[SeqParameter]]).
   *
   * {{{
   * import anorm.SeqParameter
   * SQL("SELECT * FROM Test t WHERE {categories}").
   *   on('categories -> SeqParameter(
   *     values = Seq("a", "b", "c"), separator = " OR ",
   *     pre = "EXISTS (SELECT NULL FROM j WHERE t.id=j.id AND name=",
   *     post = ")"))
   * }}}
   */
  implicit def seqParamToStatement[A](implicit c: ToStatement[Seq[A]]) =
    new ToStatement[SeqParameter[A]] with NotNullGuard {
      def set(s: PreparedStatement, offset: Int, ps: SeqParameter[A]): Unit =
        c.set(s, offset, ps.values)
    }

  @inline private def traversableToStatement[A, T <: Compat.Trav[A]](implicit c: ToStatement[A]) = new ToStatement[T] with NotNullGuard {
    def set(s: PreparedStatement, offset: Int, ps: T) =
      if (ps == (null: Compat.Trav[A])) throw new IllegalArgumentException()
      else {
        ps.foldLeft(offset) { (i, p) => c.set(s, i, p); i + 1 }
        ()
      }
  }

  /**
   * Sets an array parameter on statement (see `java.sql.Array`).
   *
   * {{{
   * SQL("INSERT INTO Table(arr) VALUES {a}").on("a" -> Array("A", "2", "C"))
   * }}}
   */
  implicit def arrayToParameter[A <: AnyRef](implicit m: ParameterMetaData[A]) =
    new ToStatement[Array[A]] with NotNullGuard {
      def set(s: PreparedStatement, i: Int, arr: Array[A]) =
        if (arr == (null: AnyRef)) throw new IllegalArgumentException()
        else s.setArray(i, s.getConnection.createArrayOf(
          m.sqlType, arr.map(a => a: AnyRef)))
    }
}

/** Meta data for Joda parameters */
object JodaParameterMetaData {
  import org.joda.time.{ DateTime, LocalDate, LocalDateTime, Instant }

  import java.sql.Types

  sealed trait JodaTimeMetaData {
    val sqlType = "TIMESTAMP"
    val jdbcType = Types.TIMESTAMP
  }

  /** Date/time parameter meta data */
  implicit object JodaDateTimeMetaData
    extends ParameterMetaData[DateTime] with JodaTimeMetaData

  /** Local date/time parameter meta data */
  implicit object JodaLocalDateTimeMetaData
    extends ParameterMetaData[LocalDateTime] with JodaTimeMetaData

  /** Instant parameter meta data */
  implicit object JodaInstantMetaData
    extends ParameterMetaData[Instant] with JodaTimeMetaData

  /** Local date parameter meta data */
  implicit object JodaLocalDateMetaData
    extends ParameterMetaData[LocalDate] with JodaTimeMetaData
}

sealed trait JodaToStatement {
  import org.joda.time.{ DateTime, LocalDate, LocalDateTime, Instant }

  /**
   * Sets joda-time DateTime as statement parameter.
   * For `null` value, `setNull` with `TIMESTAMP` is called on statement.
   *
   * {{{
   * import org.joda.time.DateTime
   *
   * SQL("UPDATE tbl SET modified = {d}").on('d -> new DateTime())
   * }}}
   */
  implicit def jodaDateTimeToStatement(implicit meta: ParameterMetaData[DateTime]): ToStatement[DateTime] = new ToStatement[DateTime] {
    def set(s: PreparedStatement, index: Int, date: DateTime): Unit =
      if (date != (null: DateTime)) {
        s.setTimestamp(index, new Timestamp(date.getMillis()))
      } else s.setNull(index, meta.jdbcType)
  }

  /**
   * Sets a local date/time on statement.
   *
   * {{{
   * import org.joda.time.LocalDateTime
   *
   * SQL("SELECT * FROM Test WHERE time < {b}").on('b -> LocalDateTime.now)
   * }}}
   */
  implicit def jodaLocalDateTimeToStatement(implicit meta: ParameterMetaData[LocalDateTime]): ToStatement[LocalDateTime] = new ToStatement[LocalDateTime] {
    def set(s: PreparedStatement, i: Int, t: LocalDateTime): Unit =
      if (t == (null: LocalDateTime)) s.setNull(i, meta.jdbcType)
      else s.setTimestamp(i, new Timestamp(t.toDateTime.getMillis))
  }

  /**
   * Sets a local date on statement.
   *
   * {{{
   * import org.joda.time.LocalDate
   *
   * SQL("SELECT * FROM Test WHERE time < {b}").on('b -> LocalDate.now)
   * }}}
   */
  implicit def jodaLocalDateToStatement(implicit meta: ParameterMetaData[LocalDate]): ToStatement[LocalDate] = new ToStatement[LocalDate] {
    def set(s: PreparedStatement, i: Int, t: LocalDate): Unit =
      if (t == (null: LocalDate)) s.setNull(i, meta.jdbcType)
      else s.setTimestamp(i, new Timestamp(t.toDate.getTime))
  }

  /**
   * Sets joda-time Instant as statement parameter.
   * For `null` value, `setNull` with `TIMESTAMP` is called on statement.
   *
   * {{{
   * import org.joda.time.Instant
   *
   * SQL("UPDATE tbl SET modified = {d}").on('d -> new Instant())
   * }}}
   */
  implicit def jodaInstantToStatement(implicit meta: ParameterMetaData[Instant]): ToStatement[Instant] = new ToStatement[Instant] {
    def set(s: PreparedStatement, index: Int, instant: Instant): Unit =
      if (instant != (null: Instant)) {
        s.setTimestamp(index, new Timestamp(instant.getMillis))
      } else s.setNull(index, meta.jdbcType)
  }
}

sealed trait JavaTimeToStatement {
  import java.time.{ Instant, LocalDate, LocalDateTime, ZonedDateTime }

  /**
   * Sets a temporal instant on statement.
   *
   * {{{
   * import java.time.Instant
   * import anorm._
   *
   * SQL("SELECT * FROM Test WHERE time < {b}").on('b -> Instant.now)
   * }}}
   */
  implicit def instantToStatement(implicit meta: ParameterMetaData[Instant]): ToStatement[Instant] = new ToStatement[Instant] {
    def set(s: PreparedStatement, i: Int, t: Instant): Unit =
      if (t == (null: Instant)) s.setNull(i, meta.jdbcType)
      else s.setTimestamp(i, Timestamp from t)
  }

  /**
   * Sets a local date/time on statement.
   *
   * {{{
   * import java.time.LocalDateTime
   * import anorm._
   *
   * SQL("SELECT * FROM Test WHERE time < {b}").on('b -> LocalDateTime.now)
   * }}}
   */
  implicit def localDateTimeToStatement(implicit meta: ParameterMetaData[LocalDateTime]): ToStatement[LocalDateTime] = new ToStatement[LocalDateTime] {
    def set(s: PreparedStatement, i: Int, t: LocalDateTime): Unit =
      if (t == (null: LocalDateTime)) s.setNull(i, meta.jdbcType)
      else s.setTimestamp(i, Timestamp valueOf t)
  }

  /**
   * Sets a local date on statement.
   *
   * {{{
   * import java.time.LocalTime
   * import anorm._
   *
   * SQL("SELECT * FROM Test WHERE time < {b}").on('b -> LocalDate.now)
   * }}}
   */
  implicit def localDateToStatement(implicit meta: ParameterMetaData[LocalDate]): ToStatement[LocalDate] = new ToStatement[LocalDate] {
    def set(s: PreparedStatement, i: Int, t: LocalDate): Unit =
      if (t == (null: LocalDate)) s.setNull(i, meta.jdbcType)
      else s.setTimestamp(i, Timestamp valueOf t.atStartOfDay())
  }

  /**
   * Sets a zoned date/time on statement.
   *
   * {{{
   * import java.time.ZonedDateTime
   * import anorm._
   *
   * SQL("SELECT * FROM Test WHERE time < {b}").on('b -> ZonedDateTime.now)
   * }}}
   */
  implicit def zonedDateTimeToStatement(implicit meta: ParameterMetaData[ZonedDateTime]): ToStatement[ZonedDateTime] = new ToStatement[ZonedDateTime] {
    def set(s: PreparedStatement, i: Int, t: ZonedDateTime): Unit =
      if (t == (null: ZonedDateTime)) s.setNull(i, meta.jdbcType)
      else s.setTimestamp(i, Timestamp from t.toInstant)
  }
}

sealed trait ToStatementPriority1 extends ToStatementPriority0 {
  /**
   * Sets an array of byte as parameter on statement.
   * For `null` value, `setNull` with `LONGVARBINARY` is called on statement.
   *
   * {{{
   * SQL("INSERT INTO Table(bin) VALUES {b}").on("b" -> arrayOfBytes)
   * }}}
   */
  implicit object byteArrayToStatement extends ToStatement[Array[Byte]] {
    val jdbcType = implicitly[ParameterMetaData[Array[Byte]]].jdbcType

    @SuppressWarnings(Array("ArrayEquals" /*null check*/ ))
    def set(s: PreparedStatement, i: Int, bin: Array[Byte]) =
      if (bin == (null: Array[Byte])) s.setNull(i, jdbcType)
      else s.setBytes(i, bin)
  }
}

/**
 * Provided conversions to set statement parameter.
 */
private[anorm] class ToStatementConversions extends ToStatementPriority1
  with JodaToStatement with JavaTimeToStatement {

  /**
   * Resolves `ToStatement` instance for the given type.
   *
   * {{{
   * import anorm.ToStatement
   *
   * val to: ToStatement[String] = ToStatement.of[String]
   * }}}
   */
  @inline def of[T](implicit to: ToStatement[T]): ToStatement[T] = to
}

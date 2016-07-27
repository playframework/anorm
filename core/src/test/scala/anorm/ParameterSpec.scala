package anorm

import java.lang.{
  Byte => JByte,
  Boolean => JBool,
  Double => JDouble,
  Float => JFloat,
  Long => JLong,
  Short => JShort
}
import java.math.{ BigDecimal => JBigDec, BigInteger }
import java.util.Date

import java.sql.{
  Array => JdbcArray,
  Blob => JdbcBlob,
  SQLFeatureNotSupportedException,
  Timestamp
}

import scala.collection.immutable.SortedSet

import acolyte.jdbc.{
  DefinedParameter => DParam,
  ParameterMetaData => ParamMeta,
  UpdateExecution
}
import acolyte.jdbc.AcolyteDSL.{ connection, handleStatement }
import acolyte.jdbc.Implicits._

class ParameterSpec
    extends org.specs2.mutable.Specification with JodaParameterSpec {

  "Parameter" title

  val jbi1 = new BigInteger("1234"); val Jbi1bd = new JBigDec("1234")
  val Jbd1 = new JBigDec(1.234d)
  val Sbd1 = BigDecimal(Jbd1)
  val sbi1 = BigInt(jbi1)
  val Date1 = new Date()
  val LocalDate1 = {
    import java.util._
    val cal = Calendar.getInstance()

    cal.setTime(Date1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    cal.getTime()
  }
  val Timestamp1 = { val ts = new Timestamp(123l); ts.setNanos(123456789); ts }
  val TsWrapper1 = new { val getTimestamp = Timestamp1 }
  val uuid1 = java.util.UUID.randomUUID; val Uuid1str = uuid1.toString
  val SqlArr = ParamMeta.Array
  val SqlStr = ParamMeta.Str
  val SqlBool = ParamMeta.Bool
  val SqlInt = ParamMeta.Int
  val SqlByte = ParamMeta.Byte
  val SqlShort = ParamMeta.Short
  val SqlLong = ParamMeta.Long
  val SqlFloat = ParamMeta.Float(1.23f)
  val SqlDouble3s = ParamMeta.Double(23.456d)
  val SqlDouble2s = ParamMeta.Double(23.45d)
  val SqlTimestamp = ParamMeta.Timestamp
  val SqlInt1 = ParamMeta.Numeric(Jbi1bd)
  val SqlDec1 = ParamMeta.Numeric(Jbd1)
  val byteArr = "Binary".getBytes
  def binStream = new java.io.ByteArrayInputStream(byteArr)
  def charStream = new java.io.StringReader("string")

  def withConnection[A](ps: (String, String)*)(f: java.sql.Connection => A): A = f(connection(handleStatement withUpdateHandler {
    case UpdateExecution("set-str ?",
      DParam("string", SqlStr) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-str ?",
      DParam(null, SqlStr) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-char ?",
      DParam("x", SqlStr) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-char ?",
      DParam(null, t) :: Nil) if (t.sqlTypeName == "CHAR") => 1 /* case ok */
    case UpdateExecution("set-false ?",
      DParam(false, SqlBool) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-true ?",
      DParam(true, SqlBool) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-bool ?",
      DParam(null, SqlBool) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-int ?",
      DParam(2, SqlInt) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-int ?",
      DParam(null, SqlInt) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-short ?",
      DParam(3, SqlShort) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-short ?",
      DParam(null, SqlShort) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-byte ?",
      DParam(4, SqlByte) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-byte ?",
      DParam(null, SqlByte) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-long ?",
      DParam(5l, SqlLong) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-long ?",
      DParam(null, SqlLong) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-float ?",
      DParam(1.23f, SqlFloat) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-float ?",
      DParam(null, SqlFloat) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-double ?",
      DParam(23.456d, SqlDouble3s) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-double ?",
      DParam(null, SqlDouble2s) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-jbi ?",
      DParam(Jbi1bd, SqlInt1) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-jbi ?",
      DParam(null, _) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-sbi ?",
      DParam(Jbi1bd, SqlInt1) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-sbi ?",
      DParam(null, _) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-jbd ?",
      DParam(Jbd1, SqlDec1) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-jbd ?",
      DParam(null, _) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-sbd ?",
      DParam(Jbd1, SqlDec1) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-sbd ?",
      DParam(null, _) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-date ?",
      DParam(Date1, SqlTimestamp) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-local-date ?",
      DParam(LocalDate1, SqlTimestamp) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-date ?",
      DParam(null, SqlTimestamp) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-timestamp ?",
      DParam(t: java.sql.Timestamp, SqlTimestamp) :: Nil) if t.getNanos == 123456789 => 1 /* case ok */
    case UpdateExecution("set-null-ts ?",
      DParam(null, SqlTimestamp) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-s-jbd ?, ?",
      DParam("string", SqlStr) :: DParam(Jbd1, SqlDec1) :: Nil) => 1 /* ok */
    case UpdateExecution("set-s-sbd ?, ?",
      DParam("string", SqlStr) :: DParam(Jbd1, SqlDec1) :: Nil) => 1 /* ok */
    case UpdateExecution("reorder-s-jbd ?, ?",
      DParam(Jbd1, SqlDec1) :: DParam("string", SqlStr) :: Nil) => 1 /* ok */
    case UpdateExecution("set-some-str ?",
      DParam("string", SqlStr) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-str-opt ?",
      DParam("some_str", SqlStr) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-str-opt ?",
      DParam(null, SqlStr) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-some-jbd ?",
      DParam(Jbd1, SqlDec1) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-some-sbd ?",
      DParam(Jbd1, SqlDec1) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-none ?", DParam(null, _) :: Nil) => 1 /* ok */
    case UpdateExecution("set-empty-opt ?", DParam(null, _) :: Nil) => 1 /*ok*/
    case UpdateExecution("no-param-placeholder", Nil) => 1 /* ok */
    case UpdateExecution("no-snd-placeholder ?",
      DParam("first", SqlStr) :: Nil) => 1 /* ok */
    case UpdateExecution("set-old ?", DParam(2l, SqlLong) :: Nil) => 1 // ok
    case UpdateExecution("set-id-str ?",
      DParam("str", SqlStr) :: Nil) => 1 /* ok */
    case UpdateExecution("set-not-assigned ?",
      DParam(null, _) :: Nil) => 1 /* ok */
    case UpdateExecution("set-list ?, ?, ?",
      DParam(1, SqlInt) :: DParam(3, SqlInt) :: DParam(7, _) :: Nil) => 1 // ok
    case UpdateExecution("set-seq ?, ?, ?",
      DParam("a", _) :: DParam("b", _) :: DParam("c", _) :: Nil) => 1 /* ok */
    case UpdateExecution("set-set ?, ?, ?",
      DParam(1, SqlInt) :: DParam(3, SqlInt) :: DParam(7, _) :: Nil) => 1 // ok
    case UpdateExecution("set-sortedset ?, ?, ?",
      DParam("a", _) :: DParam("b", _) :: DParam("c", _) :: Nil) => 1 /* ok */
    case UpdateExecution("set-stream ?, ?, ?",
      DParam(1, SqlInt) :: DParam(3, SqlInt) :: DParam(7, _) :: Nil) => 1 // ok
    case UpdateExecution("set-vector ?, ?, ?",
      DParam("a", _) :: DParam("b", _) :: DParam("c", _) :: Nil) => 1 /* ok */
    case UpdateExecution("set-null-list ?",
      DParam(null, SqlInt) :: Nil) => 1 /* ok */
    case UpdateExecution("set-null-seq ?",
      DParam(null, SqlStr) :: Nil) => 1 /* ok */
    case UpdateExecution("set-null-set ?",
      DParam(null, SqlInt) :: Nil) => 1 /* ok */
    case UpdateExecution("set-null-sortedset ?",
      DParam(null, SqlStr) :: Nil) => 1 /* ok */
    case UpdateExecution("set-null-stream ?",
      DParam(null, SqlInt) :: Nil) => 1 /* ok */
    case UpdateExecution("set-null-vector ?",
      DParam(null, SqlStr) :: Nil) => 1 /* ok */
    case UpdateExecution("set-seqp cat = ? OR cat = ? OR cat = ?",
      DParam(1.2f, _) :: DParam(23.4f, _) :: DParam(5.6f, _) :: Nil) => 1 // ok
    case UpdateExecution("set-uuid ?",
      DParam(Uuid1str, SqlStr) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-null-uuid ?",
      DParam(null, SqlStr) :: Nil) => 1 /* case ok */
    case UpdateExecution("set-array ?", DParam(
      a: JdbcArray, SqlArr) :: Nil) if (
      a.getArray.asInstanceOf[Array[_]].toList == List("a", "b", "c")) => 1 // ok
    case UpdateExecution("set-binary ?", DParam(b: Array[Byte], _) :: Nil) if (
      new String(b) == "Binary") => 1
    case UpdateExecution("set-null-binary ?", DParam(b, _) :: Nil) if (
      b == null) => 1

    case UpdateExecution("set-blob ?", DParam(b: JdbcBlob, _) :: Nil) if (
      new String(b.getBytes(1, b.length.toInt)) == "Binary") => 1

    case UpdateExecution("set-null-blob ?", DParam(b, _) :: Nil) if (
      b == null) => 1

  }, ps: _*))

  "Named parameters" should {
    shapeless.test.illTyped {
      """("str".asInstanceOf[Any] -> 2) : NamedParameter"""
    }

    shapeless.test.illTyped {
      """"str".asInstanceOf[Any] : ParameterValue"""
    }

    "be one string with string name" in withConnection() { implicit c =>
      SQL("set-str {a}").on("a" -> "string").
        aka("query") must beLike {
          case q @ SimpleSql( // check accross construction
            SqlQuery(TokenizedStatement(List(
              TokenGroup(List(StringToken("set-str ")), Some("a"))), List("a")),
              List("a"), _), ps, _, _) if (ps contains "a") =>

            // execute = false: update ok but returns no resultset
            // see java.sql.PreparedStatement#execute
            q.execute() aka "execution" must beFalse
        }
    }

    "be one string with symbol name" in withConnection() { implicit c =>
      SQL("set-str {b}").on('b -> "string").
        aka("query") must beLike {
          case q @ SimpleSql( // check accross construction
            SqlQuery(TokenizedStatement(List(
              TokenGroup(List(StringToken("set-str ")), Some("b"))), List("b")),
              List("b"), _), ps, _, _) if (ps contains "b") =>
            q.execute() aka "execution" must beFalse
        }
    }

    "be one string with string interpolation" in withConnection() {
      implicit c =>
        SQL"""set-str ${"string"}""".
          aka("query") must beLike {
            case q @ SimpleSql( // check accross construction
              SqlQuery(TokenizedStatement(List(
                TokenGroup(List(StringToken("set-str ")), Some("_0"))),
                List("_0")), List("_0"), _), ps, _, _) if (
              ps contains "_0") => q.execute() aka "execution" must beFalse

          }
    }

    "be null string" in withConnection() { implicit c =>
      SQL("set-null-str {p}").on("p" -> null.asInstanceOf[String]).
        execute() must beFalse
    }

    "be undefined string" in withConnection() { implicit c =>
      SQL("set-null-str {p}").on("p" -> Option.empty[String]).
        execute() must beFalse
    }

    "be array of byte" in withConnection() { implicit c =>
      SQL("set-binary {p}").on("p" -> byteArr).execute() must beFalse
    }

    "be null array of byte" in withConnection() { implicit c =>
      SQL"set-null-binary ${null.asInstanceOf[Array[Byte]]}".
        execute() must beFalse
    }

    "be binary stream" in withConnection() { implicit c =>
      SQL"set-binary ${binStream}".execute() must beFalse
    }

    "be null binary stream" in withConnection() { implicit c =>
      SQL("set-null-binary {p}").on("p" ->
        null.asInstanceOf[java.io.InputStream]).execute() must beFalse
    }

    "be blob" in withConnection() { implicit c =>
      val blob = c.createBlob()
      blob.setBytes(1, byteArr)
      SQL("set-blob {p}").on("p" -> blob).execute() must beFalse
    }

    "be null blob" in withConnection() { implicit c =>
      SQL("set-null-blob {p}").on("p" -> null.asInstanceOf[java.sql.Blob]).
        execute() must beFalse
    }

    "be character 'x'" in withConnection() { implicit c =>
      (SQL("set-char {b}").on('b -> new java.lang.Character('x')).
        aka("query") must beLike {
          case q @ SimpleSql( // check accross construction
            SqlQuery(TokenizedStatement(List(
              TokenGroup(List(StringToken("set-char ")), Some("b"))),
              List("b")), List("b"), _), ps, _, _) if (
            ps contains "b") => q.execute() aka "execution" must beFalse
        }).and(SQL("set-char {b}").on('b -> Character.valueOf('x')).
          execute() aka "execution" must beFalse)
    }

    "be null character" in withConnection() { implicit c =>
      SQL("set-null-char {p}").on("p" -> null.asInstanceOf[Character]).
        execute() must beFalse
    }

    "be undefined character" in withConnection() { implicit c =>
      SQL("set-null-char {p}").on("p" -> Option.empty[Char]).
        execute() must beFalse
    }

    "be boolean true" in withConnection() { implicit c =>
      (SQL("set-true {p}").on("p" -> true).execute() must beFalse).
        and(SQL("set-true {p}").on("p" -> JBool.TRUE).execute() must beFalse)
    }

    "be boolean false" in withConnection() { implicit c =>
      (SQL("set-false {p}").on("p" -> false).execute() must beFalse).
        and(SQL("set-false {p}").on("p" -> JBool.FALSE).execute() must beFalse)
    }

    "be null boolean" in withConnection() { implicit c =>
      SQL("set-null-bool {p}").on('p -> null.asInstanceOf[JBool]).execute().
        aka("execution") must beFalse
    }

    "be undefined boolean" in withConnection() { implicit c =>
      SQL("set-null-bool {p}").on('p -> Option.empty[Boolean]).execute().
        aka("execution") must beFalse
    }

    "be int" in withConnection() { implicit c =>
      (SQL("set-int {p}").on("p" -> 2).execute() must beFalse).
        and(SQL("set-int {p}").on("p" -> new Integer(2)).execute() must beFalse)
    }

    "be null int" in withConnection() { implicit c =>
      SQL("set-null-int {p}").on("p" -> null.asInstanceOf[Integer]).
        execute() must beFalse
    }

    "be undefined int" in withConnection() { implicit c =>
      SQL("set-null-int {p}").on("p" -> Option.empty[Int]).
        execute() must beFalse
    }

    "be short" in withConnection() { implicit c =>
      (SQL("set-short {p}").on("p" -> 3.toShort).execute() must beFalse).
        and(SQL("set-short {p}").on(
          "p" -> new JShort("3")).execute() must beFalse)
    }

    "be null short" in withConnection() { implicit c =>
      SQL("set-null-short {p}").on(
        "p" -> null.asInstanceOf[JShort]).execute() must beFalse
    }

    "be undefined short" in withConnection() { implicit c =>
      SQL("set-null-short {p}").on(
        "p" -> Option.empty[Short]).execute() must beFalse
    }

    "be byte" in withConnection() { implicit c =>
      (SQL("set-byte {p}").on("p" -> 4.toByte).execute() must beFalse).
        and(SQL("set-byte {p}").on(
          "p" -> new JByte("4")).execute() must beFalse)
    }

    "be null byte" in withConnection() { implicit c =>
      SQL("set-null-byte {p}").on("p" -> null.asInstanceOf[JByte]).
        execute() must beFalse
    }

    "be undefined byte" in withConnection() { implicit c =>
      SQL("set-null-byte {p}").on("p" -> Option.empty[Byte]).
        execute() must beFalse
    }

    "be long" in withConnection() { implicit c =>
      (SQL("set-long {p}").on("p" -> 5l).execute() must beFalse).
        and(SQL("set-long {p}").on(
          "p" -> new JLong(5)).execute() must beFalse)
    }

    "be null long" in withConnection() { implicit c =>
      SQL("set-null-long {p}").on("p" -> null.asInstanceOf[JLong]).
        execute() must beFalse
    }

    "be undefined long" in withConnection() { implicit c =>
      SQL("set-null-long {p}").on("p" -> Option.empty[Long]).
        execute() must beFalse
    }

    "be float" in withConnection() { implicit c =>
      (SQL("set-float {p}").on("p" -> 1.23f).execute() must beFalse).
        and(SQL("set-float {p}").on(
          "p" -> new JFloat(1.23f)).execute() must beFalse)
    }

    "be null float" in withConnection() { implicit c =>
      SQL("set-null-float {p}").on("p" -> null.asInstanceOf[JFloat]).
        execute() must beFalse
    }

    "be undefined float" in withConnection() { implicit c =>
      SQL("set-null-float {p}").on("p" -> Option.empty[Float]).
        execute() must beFalse
    }

    "be double" in withConnection() { implicit c =>
      (SQL("set-double {p}").on("p" -> 23.456d).execute() must beFalse).
        and(SQL("set-double {p}").on(
          "p" -> new JDouble(23.456d)).execute() must beFalse)
    }

    "be null double" in withConnection() { implicit c =>
      SQL("set-null-double {p}").on(
        "p" -> null.asInstanceOf[JDouble]).execute() must beFalse
    }

    "be undefined double" in withConnection() { implicit c =>
      SQL("set-null-double {p}").on(
        "p" -> Option.empty[Double]).execute() must beFalse
    }

    "be Java big integer" in withConnection() { implicit c =>
      SQL("set-jbi {p}").on("p" -> jbi1).execute() must beFalse
    }

    "be null Java big integer" in withConnection() { implicit c =>
      SQL("set-null-jbi {p}").
        on("p" -> null.asInstanceOf[BigInteger]).execute() must beFalse
    }

    "be Scala big integer" in withConnection() { implicit c =>
      SQL("set-sbi {p}").on("p" -> sbi1).execute() must beFalse
    }

    "be null Scala big integer" in withConnection() { implicit c =>
      SQL("set-null-sbi {p}").on("p" -> null.asInstanceOf[BigInt]).
        execute() must beFalse
    }

    "be undefined Scala big integer" in withConnection() { implicit c =>
      SQL("set-null-sbi {p}").on("p" -> Option.empty[BigInt]).
        execute() must beFalse
    }

    "be Java big decimal" in withConnection() { implicit c =>
      SQL("set-jbd {p}").on("p" -> Jbd1).execute() must beFalse
    }

    "be null Java big decimal" in withConnection() { implicit c =>
      SQL("set-null-jbd {p}").
        on("p" -> null.asInstanceOf[JBigDec]).execute() must beFalse
    }

    "be Scala big decimal" in withConnection() { implicit c =>
      SQL("set-sbd {p}").on("p" -> Sbd1).execute() must beFalse
    }

    "be null Scala big decimal" in withConnection() { implicit c =>
      SQL("set-null-sbd {p}").
        on("p" -> null.asInstanceOf[BigDecimal]).execute() must beFalse
    }

    "be undefined Scala big decimal" in withConnection() { implicit c =>
      SQL("set-null-sbd {p}").
        on("p" -> Option.empty[BigDecimal]).execute() must beFalse
    }

    "be date" in withConnection() { implicit c =>
      SQL("set-date {p}").on("p" -> Date1).execute() must beFalse
    }

    "be null date" in withConnection() { implicit c =>
      SQL("set-null-date {p}").
        on("p" -> null.asInstanceOf[Date]).execute() must beFalse
    }

    "be undefined date" in withConnection() { implicit c =>
      SQL("set-null-date {p}").
        on("p" -> Option.empty[Date]).execute() must beFalse
    }

    "be not null timestamp" >> {
      "directly" in withConnection() { implicit c =>
        SQL("set-timestamp {p}").on("p" -> Timestamp1).execute() must beFalse
      }

      "wrapped" in withConnection() { implicit c =>
        SQL("set-timestamp {p}").on("p" -> TsWrapper1).execute() must beFalse
      }
    }

    "be null timestamp" >> {
      "directly" in withConnection() { implicit c =>
        SQL("set-null-ts {p}").
          on("p" -> null.asInstanceOf[Timestamp]).execute() must beFalse
      }

      "wrapped" in withConnection() { implicit c =>
        SQL("set-null-ts {p}").
          on("p" -> null.asInstanceOf[TimestampWrapper1]).execute() must beFalse

      }
    }

    "be undefined timestamp" >> {
      "directly" in withConnection() { implicit c =>
        SQL("set-null-ts {p}").
          on("p" -> Option.empty[Timestamp]).execute() must beFalse
      }

      "wrapped" in withConnection() { implicit c =>
        SQL("set-null-ts {p}").
          on("p" -> Option.empty[TimestampWrapper1]).execute() must beFalse
      }
    }

    "be UUID" in withConnection() { implicit c =>
      SQL("set-uuid {p}").on("p" -> uuid1).execute() must beFalse
    }

    "be null UUID" in withConnection() { implicit c =>
      SQL("set-null-uuid {p}").
        on("p" -> null.asInstanceOf[java.util.UUID]).execute() must beFalse
    }

    "be undefined UUID" in withConnection() { implicit c =>
      SQL("set-null-uuid {p}").
        on("p" -> Option.empty[java.util.UUID]).execute() must beFalse
    }

    "be multiple (string, Java big decimal)" in withConnection() {
      implicit c =>
        SQL("set-s-jbd {a}, {b}").on("a" -> "string", "b" -> Jbd1).
          aka("query") must beLike {
            case q @ SimpleSql(
              SqlQuery(TokenizedStatement(List(
                TokenGroup(List(StringToken("set-s-jbd ")), Some("a")),
                TokenGroup(List(StringToken(", ")), Some("b"))),
                List("a", "b")), List("a", "b"), _), ps, _, _) if (
              ps.contains("a") && ps.contains("b")) =>
              q.execute() aka "execution" must beFalse

          }
    }

    "be multiple (string, Scala big decimal)" in withConnection() {
      implicit c =>
        SQL("set-s-sbd {a}, {b}").on("a" -> "string", "b" -> Sbd1).
          execute() aka "execution" must beFalse
    }

    "be multiple (string, Scala big decimal) with string interpolation" in withConnection() {
      implicit c =>
        SQL"""set-s-sbd ${"string"}, $Sbd1""".
          execute() aka "execution" must beFalse
    }

    "be reordered" in withConnection() { implicit c =>
      SQL("reorder-s-jbd {b}, {a}").on("a" -> "string", "b" -> Jbd1).
        aka("query") must beLike {
          case q @ SimpleSql(
            SqlQuery(TokenizedStatement(List(
              TokenGroup(List(StringToken("reorder-s-jbd ")), Some("b")),
              TokenGroup(List(StringToken(", ")), Some("a"))),
              List("b", "a")), List("b", "a"), _), ps, _, _) if (
            ps.contains("a") && ps.contains("b")) =>
            q.execute() aka "execution" must beFalse

        }
    }

    "be defined string option as Some[String]" in withConnection() {
      implicit c =>
        SQL("set-some-str {p}").on("p" -> Some("string")).execute().
          aka("execution") must beFalse
    }

    "be defined string option as Option[String]" in withConnection() {
      implicit c =>
        SQL("set-str-opt {p}").on("p" -> Option("some_str")).execute().
          aka("execution") must beFalse
    }

    "be null string option as Option[String]" in withConnection(
      "acolyte.parameter.untypedNull" -> "true") {
        implicit c =>
          (SQL("set-null-str-opt {p}").
            on("p" -> null.asInstanceOf[Some[String]]).
            aka("parameter conversion") must throwA[IllegalArgumentException]).
            and(SQL("set-null-str-opt {p}").
              on("p" -> null.asInstanceOf[Option[String]]).
              aka("conversion") must throwA[IllegalArgumentException])
      }

    "be defined Java big decimal option" in withConnection() { implicit c =>
      SQL("set-some-jbd {p}").on("p" -> Some(Jbd1)).
        execute() aka "execution" must beFalse

    }

    "be defined Scala big decimal option" in withConnection() { implicit c =>
      SQL("set-some-sbd {p}").on("p" -> Some(Sbd1)).
        execute() aka "execution" must beFalse

    }

    "not be set if placeholder not found in SQL" in withConnection() {
      implicit c =>
        SQL("no-param-placeholder").on("p" -> "not set").execute().
          aka("execution") must beFalse

    }

    "be partially set if matching placeholder is missing for second one" in {
      withConnection() { implicit c =>
        SQL("no-snd-placeholder {a}")
          .on("a" -> "first", "b" -> "second").execute() must beFalse

      }
    }

    "set null parameter from None" in withConnection(
      "acolyte.parameter.untypedNull" -> "true") { implicit c =>
        /*
       http://docs.oracle.com/javase/6/docs/api/java/sql/PreparedStatement.html#setObject%28int,%20java.lang.Object%29
       -> Note: Not all databases allow for a non-typed Null to be sent to the backend. For maximum portability, the setNull or the setObject(int parameterIndex, Object x, int sqlType) method should be used instead of setObject(int parameterIndex, Object x).
       -> Note: This method throws an exception if there is an ambiguity, for example, if the object is of a class implementing more than one of the interfaces named above. 
       */
        SQL("set-none {p}").on("p" -> None).
          execute() aka "execution" must beFalse
      }

    "set null parameter from empty option" in withConnection(
      "acolyte.parameter.untypedNull" -> "true") { implicit c =>
        SQL("set-empty-opt {p}").on("p" -> Option.empty[String]).
          execute() aka "execution" must beFalse
      }

    withConnection() { implicit c =>
      // Do not accept untyped name when feature enabled
      val name: Any = "untyped"

      shapeless.test.illTyped {
        """SQL("set-old {untyped}").on(name -> 2l)"""
      }
    }

    withConnection() { implicit c =>
      // Do not accept untyped value when feature enabled
      val d: Any = new java.util.Date()

      shapeless.test.illTyped {
        """val params: Seq[NamedParameter] = Seq("mod" -> d, "id" -> "idv")"""
      }
    }

    "accept value wrapped as opaque parameter object" in withConnection() {
      implicit c =>
        SQL("set-date {d}").on('d -> anorm.Object(new java.util.Date())).
          execute aka "execution" must throwA[SQLFeatureNotSupportedException](
            message = "Unsupported parameter type: java.util.Date")

    }

    "for multi-value without null" >> {
      "accept List" in withConnection() { implicit c =>
        SQL("set-list {list}").on('list -> List(1, 3, 7)).
          aka("query") must beLike {
            case q @ SimpleSql(SqlQuery(
              TokenizedStatement(List(TokenGroup(List(StringToken("set-list ")), Some("list"))), List("list")), "list" :: Nil, _), ps, _, _) if (
              ps.size == 1 && ps.contains("list")) =>
              q.execute() aka "execution" must beFalse
          }
      }

      "accept Seq" in withConnection() { implicit c =>
        SQL("set-seq {seq}").
          on('seq -> Seq("a", "b", "c")) aka "query" must beLike {
            case q @ SimpleSql(SqlQuery(
              TokenizedStatement(List(TokenGroup(List(StringToken("set-seq ")), Some("seq"))), List("seq")), "seq" :: Nil, _), ps, _, _) if (
              ps.size == 1 && ps.contains("seq")) =>
              q.execute() aka "execution" must beFalse
          }
      }

      "accept Set" in withConnection() { implicit c =>
        SQL("set-set {set}").on('set -> Set(1, 3, 7)).
          aka("query") must beLike {
            case q @ SimpleSql(
              SqlQuery(TokenizedStatement(List(TokenGroup(List(StringToken("set-set ")), Some("set"))), List("set")), "set" :: Nil, _), ps, _, _) if (
              ps.size == 1 && ps.contains("set")) =>
              q.execute() aka "execution" must beFalse
          }
      }

      "accept SortedSet" in withConnection() { implicit c =>
        SQL("set-sortedset {sortedset}").
          on('sortedset -> SortedSet("a", "b", "c")) aka "query" must beLike {
            case q @ SimpleSql(
              SqlQuery(TokenizedStatement(List(TokenGroup(List(StringToken("set-sortedset ")), Some("sortedset"))), List("sortedset")), "sortedset" :: Nil, _), ps, _, _) if (
              ps.size == 1 && ps.contains("sortedset")) =>
              q.execute() aka "execution" must beFalse
          }
      }

      "accept Stream" in withConnection() { implicit c =>
        SQL("set-stream {stream}").on('stream -> Stream(1, 3, 7)).
          aka("query") must beLike {
            case q @ SimpleSql(SqlQuery(TokenizedStatement(List(TokenGroup(
              List(StringToken("set-stream ")), Some("stream"))),
              List("stream")), "stream" :: Nil, _), ps, _, _) if (
              ps.size == 1 && ps.contains("stream")) =>
              q.execute() aka "execution" must beFalse
          }
      }

      "accept Vector" in withConnection() { implicit c =>
        SQL("set-vector {vector}").
          on('vector -> Vector("a", "b", "c")) aka "query" must beLike {
            case q @ SimpleSql(
              SqlQuery(TokenizedStatement(List(TokenGroup(List(StringToken("set-vector ")), Some("vector"))), List("vector")), "vector" :: Nil, _), ps, _, _) if (
              ps.size == 1 && ps.contains("vector")) =>
              q.execute() aka "execution" must beFalse
          }
      }

      "accept Array of String" in withConnection() { implicit c =>
        SQL("set-array {array}").on("array" -> Array("a", "b", "c")).
          aka("query") must beLike {
            case q @ SimpleSql(
              SqlQuery(TokenizedStatement(List(TokenGroup(List(StringToken("set-array ")), Some("array"))), List("array")), "array" :: Nil, _), ps, _, _) if (
              ps.size == 1 && ps.contains("array")) =>
              q.execute() aka "execution" must beFalse
          }
      }
    }

    "refuse multi-value" >> {
      "for List" in withConnection() { implicit c =>
        SQL("set-null-seq {list}").on('list -> null.asInstanceOf[List[String]]).
          aka("parameter conversion") must throwA[IllegalArgumentException]

      }

      "for Seq" in withConnection() { implicit c =>
        SQL("set-null-seq {seq}").on('seq -> null.asInstanceOf[Seq[String]]).
          aka("parameter conversion") must throwA[IllegalArgumentException]

      }

      "for Set" in withConnection() { implicit c =>
        SQL("set-null-seq {set}").on('set -> null.asInstanceOf[Set[String]]).
          aka("parameter conversion") must throwA[IllegalArgumentException]

      }

      "for SortedSet" in withConnection() { implicit c =>
        SQL("sortedSet-null-seq {sortedSet}").on('sortedSet -> null.asInstanceOf[SortedSet[String]]).
          aka("parameter conversion") must throwA[IllegalArgumentException]

      }

      "for Stream" in withConnection() { implicit c =>
        SQL("stream-null-seq {stream}").on(
          'stream -> null.asInstanceOf[Stream[String]]).
          aka("parameter conversion") must throwA[IllegalArgumentException]

      }

      "for Vector" in withConnection() { implicit c =>
        SQL("vector-null-seq {vector}").on(
          'vector -> null.asInstanceOf[Vector[String]]).
          aka("parameter conversion") must throwA[IllegalArgumentException]

      }
    }

    "set formatted value from sequence" in withConnection() { implicit c =>
      SQL("set-seqp {p}").on('p ->
        SeqParameter(Seq(1.2f, 23.4f, 5.6f), " OR ", "cat = ")).
        aka("query") must beLike {
          case q @ SimpleSql(
            SqlQuery(TokenizedStatement(List(TokenGroup(List(StringToken("set-seqp ")), Some("p"))), List("p")), "p" :: Nil, _), ps, _, _) if (
            ps.size == 1 && ps.contains("p")) =>
            q.execute() aka "execution" must beFalse
        }
    }

    "for multi-value in string interpolation" >> {
      "accept custom formatting" in withConnection() { implicit c =>
        SQL"""set-seqp ${SeqParameter(Seq(1.2f, 23.4f, 5.6f), " OR ", "cat = ")}""" aka "query" must beLike {
          case q @ SimpleSql(
            SqlQuery(TokenizedStatement(List(TokenGroup(List(StringToken("set-seqp ")), Some("_0"))), List("_0")), "_0" :: Nil, _), ps, _, _) if (
            ps.size == 1 && ps.contains("_0")) =>
            q.execute() aka "execution" must beFalse

        }
      }

      "accept Seq" in withConnection() { implicit c =>
        SQL"""set-seq ${Seq("a", "b", "c")}""" aka "query" must beLike {
          case q @ SimpleSql(
            SqlQuery(TokenizedStatement(List(TokenGroup(List(StringToken("set-seq ")), Some("_0"))), List("_0")), "_0" :: Nil, _), ps, _, _) if (
            ps.size == 1 && ps.contains("_0")) =>
            q.execute() aka "execution" must beFalse
        }
      }
    }
  }

  "Parameter in order" should {
    "be one string" in withConnection() { implicit c =>
      SQL("set-str {p}").onParams(pv("string")) aka "query" must beLike {
        case q @ SimpleSql( // check accross construction
          SqlQuery(TokenizedStatement(List(TokenGroup(List(StringToken("set-str ")), Some("p"))), List("p")), List("p"), _), ps, _, _) if (ps contains "p") =>

          // execute = false: update ok but returns no resultset
          // see java.sql.PreparedStatement#execute
          q.execute() aka "execution" must beFalse
      }
    }

    "be array of byte" in withConnection() { implicit c =>
      SQL("set-binary {p}").onParams(pv(byteArr)).execute() must beFalse
    }

    "be null array of byte" in withConnection() { implicit c =>
      SQL("set-null-binary {p}").onParams(pv(null.asInstanceOf[Array[Byte]])).
        execute() must beFalse
    }

    "be binary stream" in withConnection() { implicit c =>
      SQL("set-binary {p}").onParams(pv(binStream)).execute() must beFalse
    }

    "be null binary stream" in withConnection() { implicit c =>
      SQL("set-null-binary {p}").onParams(pv(
        null.asInstanceOf[java.io.InputStream])).execute() must beFalse
    }

    "be blob" in withConnection() { implicit c =>
      val blob = c.createBlob()
      blob.setBytes(1, byteArr)
      SQL("set-blob {p}").onParams(pv(blob)).execute() must beFalse
    }

    "be null blob" in withConnection() { implicit c =>
      SQL("set-null-blob {p}").onParams(pv(null.asInstanceOf[java.sql.Blob])).
        execute() must beFalse
    }

    "be boolean true" in withConnection() { implicit c =>
      SQL("set-true {p}").onParams(pv(true)).execute() must beFalse
    }

    "be boolean false" in withConnection() { implicit c =>
      SQL("set-false {p}").onParams(pv(false)).execute() must beFalse
    }

    "be short" in withConnection() { implicit c =>
      SQL("set-short {p}").onParams(pv(3.toShort)).execute() must beFalse
    }

    "be byte" in withConnection() { implicit c =>
      SQL("set-byte {p}").onParams(pv(4.toByte)).execute() must beFalse
    }

    "be long" in withConnection() { implicit c =>
      SQL("set-long {p}").onParams(pv(5l)).execute() must beFalse
    }

    "be float" in withConnection() { implicit c =>
      SQL("set-float {p}").onParams(pv(1.23f)).execute() must beFalse
    }

    "be double" in withConnection() { implicit c =>
      SQL("set-double {p}").onParams(pv(23.456d)).execute() must beFalse
    }

    "be one Java big integer" in withConnection() { implicit c =>
      SQL("set-jbi {p}").onParams(pv(jbi1)).execute() must beFalse
    }

    "be one Scala big integer" in withConnection() { implicit c =>
      SQL("set-sbi {p}").onParams(pv(sbi1)).execute() must beFalse
    }

    "be one Java big decimal" in withConnection() { implicit c =>
      SQL("set-jbd {p}").onParams(pv(Jbd1)).execute() must beFalse
    }

    "be one Scala big decimal" in withConnection() { implicit c =>
      SQL("set-sbd {p}").onParams(pv(Sbd1)).execute() must beFalse
    }

    "be one date" in withConnection() { implicit c =>
      SQL("set-date {p}").onParams(pv(Date1)).execute() must beFalse
    }

    "be one timestamp" in withConnection() { implicit c =>
      SQL("set-timestamp {p}").onParams(pv(Timestamp1)).execute() must beFalse
    }

    "be multiple (string, Java big decimal)" in withConnection() {
      implicit c =>
        SQL("set-s-jbd {a}, {b}").onParams(pv("string"), pv(Jbd1)).
          aka("query") must beLike {
            case q @ SimpleSql(
              SqlQuery(TokenizedStatement(List(
                TokenGroup(List(StringToken("set-s-jbd ")), Some("a")),
                TokenGroup(List(StringToken(", ")), Some("b"))),
                List("a", "b")), List("a", "b"), _), ps, _, _) if (
              ps.contains("a") && ps.contains("b")) =>
              q.execute() aka "execution" must beFalse

          }
    }

    "be multiple (string, Scala big decimal)" in withConnection() {
      implicit c =>
        SQL("set-s-sbd {a}, {b}").onParams(pv("string"), pv(Sbd1)).
          execute() aka "execution" must beFalse

    }

    "be defined string option as Some[String]" in withConnection() {
      implicit c =>
        SQL("set-some-str ?").copy(paramsInitialOrder = List("p")).
          onParams(pv(Some("string"))).execute() aka "execution" must beFalse
    }

    "be defined string option as Option[String]" in withConnection() {
      implicit c =>
        SQL("set-some-str ?").copy(paramsInitialOrder = List("p")).
          onParams(pv(Option("string"))).
          execute() aka "execution" must beFalse
    }

    "be defined Java big decimal option" in withConnection() { implicit c =>
      SQL("set-some-jbd {p}").
        onParams(pv(Some(Jbd1))).execute() aka "execute" must beFalse

    }

    "be defined Scala big decimal option" in withConnection() { implicit c =>
      SQL("set-some-sbd {p}").onParams(pv(Some(Sbd1))).
        execute() aka "execute" must beFalse

    }

    "set null parameter from None" in withConnection(
      "acolyte.parameter.untypedNull" -> "true") { implicit c =>
        /*
       http://docs.oracle.com/javase/6/docs/api/java/sql/PreparedStatement.html#setObject%28int,%20java.lang.Object%29
       */
        SQL("set-none {p}").onParams(pv(None)).
          execute() aka "execution" must beFalse
      }

    "set null parameter from empty option" in withConnection(
      "acolyte.parameter.untypedNull" -> "true") { implicit c =>
        SQL("set-empty-opt {p}").onParams(pv(Option.empty[String])).
          execute() aka "execution" must beFalse
      }

    "not be set if placeholder not found in SQL" in withConnection() {
      implicit c =>
        SQL("no-param-placeholder").onParams(pv("not set")).execute().
          aka("execution") must beFalse

    }

    "be partially set if matching placeholder is missing for second one" in {
      withConnection() { implicit c =>
        SQL("no-snd-placeholder ?").copy(paramsInitialOrder = List("p")).
          onParams(pv("first"), pv("second")).execute() must beFalse

      }
    }
  }

  private def pv[A](v: A)(implicit s: ToSql[A] = null, p: ToStatement[A]) =
    ParameterValue(v, s, p)

}

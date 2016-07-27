package anorm

import java.io.{ ByteArrayInputStream, InputStream }
import java.sql.{ Array => SqlArray }
import javax.sql.rowset.serial.{ SerialBlob, SerialClob }
import java.math.BigInteger
import java.util.UUID

import scala.util.Random

import acolyte.jdbc.{ QueryResult, ImmutableArray }
import acolyte.jdbc.RowLists.{
  bigDecimalList,
  binaryList,
  booleanList,
  byteList,
  dateList,
  doubleList,
  floatList,
  intList,
  stringList,
  longList,
  rowList1,
  shortList,
  streamList,
  timeList,
  timestampList
}
import acolyte.jdbc.AcolyteDSL.{ connection, handleQuery, withQueryResult }
import acolyte.jdbc.Implicits._

import SqlParser.{ byte, double, float, int, long, scalar, short }

object ColumnSpec
    extends org.specs2.mutable.Specification with JodaColumnSpec {

  "Column" title

  trait TSWrapper { def stringValue: String }
  val tssw1 = new TSWrapper {
    lazy val stringValue = "oracleRowId1235"
  }

  val bd = new java.math.BigDecimal("34.5679")
  val bi = new java.math.BigInteger("1234")
  val clob = new SerialClob(Array[Char]('a', 'b', 'c', 'd', 'e', 'f'))
  val bindata = Array[Byte](1, 2, 3, 5)
  lazy val binstream = new ByteArrayInputStream(bindata)

  "Column mapping as bytes array" should {
    "be parsed from bytes array" in withQueryResult(binaryList :+ bindata) {
      implicit con =>
        SQL("SELECT a").as(scalar[Array[Byte]].single).
          aka("parsed bytes") must_== bindata
    }

    "be parsed from binary stream" in withQueryResult(
      streamList :+ new ByteArrayInputStream(bindata)) { implicit con =>
        SQL("SELECT a").as(scalar[Array[Byte]].single).
          aka("parsed bytes") must_== bindata
      }

    "be parsed from blob" in withQueryResult(
      rowList1(classOf[SerialBlob]) :+ new SerialBlob(bindata)) {
        implicit con =>
          SQL("SELECT a").as(scalar[Array[Byte]].single).
            aka("parsed bytes") must_== bindata
      }

    "be parsed from string" in withQueryResult(stringList :+ "strbytes") {
      implicit con =>
        SQL("SELECT a").as(scalar[Array[Byte]].single).
          aka("parsed bytes") must_== "strbytes".getBytes
    }

    "be parsed from oracle.sql.ROWID" in withQueryResult(
      rowList1(classOf[TSWrapper]) :+ tssw1) { implicit con =>
        SQL("SELECT a").as(scalar[Array[Byte]].single).
          aka("parsed bytes") must_== "oracleRowId1235".getBytes
      }

    "have convinence mapping function" in withQueryResult(
      binaryList.withLabel(1, "bin") :+ bindata) { implicit con =>

        SQL("SELECT bin").as(SqlParser.byteArray("bin").single).
          aka("parsed bytes") must_== bindata
      }
  }

  "Column mapping as binary stream" should {
    def withBytes[T](in: InputStream)(f: Array[Byte] => T): T =
      f(scala.io.Source.fromInputStream(in).mkString.getBytes)

    "be parsed from bytes array" in withQueryResult(binaryList :+ bindata) {
      implicit con =>
        SQL("SELECT a").as(scalar[InputStream].single).
          aka("parsed stream") must beLike {
            case stream => withBytes(stream)(_ aka "content" must_== bindata)
          }
    }

    "be parsed from binary stream" in withQueryResult(
      streamList :+ new ByteArrayInputStream(bindata)) { implicit con =>
        SQL("SELECT a").as(scalar[InputStream].single).
          aka("parsed stream") must beLike {
            case stream => withBytes(stream)(_ aka "content" must_== bindata)
          }
      }

    "be parsed from blob" in withQueryResult(
      rowList1(classOf[SerialBlob]) :+ new SerialBlob(bindata)) {
        implicit con =>
          SQL("SELECT a").as(scalar[InputStream].single).
            aka("parsed stream") must beLike {
              case stream => withBytes(stream)(_ aka "content" must_== bindata)
            }
      }

    "be parsed from string" in withQueryResult(stringList :+ "strbytes") {
      implicit con =>
        SQL("SELECT a").as(scalar[InputStream].single).
          aka("parsed stream") must beLike {
            case stream =>
              withBytes(stream)(_ aka "content" must_== "strbytes".getBytes)
          }
    }

    "have convinence mapping function" in withQueryResult(
      streamList.withLabel(1, "bin") :+ binstream) { implicit con =>

        SQL("SELECT bin").as(SqlParser.binaryStream("bin").single).
          aka("parsed stream") must beLike {
            case stream => withBytes(stream)(_ aka "content" must_== bindata)
          }
      }
  }

  "Column mapped as character" should {
    "be parsed from string" in withQueryResult(stringList :+ "abc") {
      implicit con =>
        SQL("SELECT c").as(scalar[Char].single) aka "parsed char" must_== 'a'
    }

    "be parsed from clob" in withQueryResult(
      rowList1(classOf[SerialClob]) :+ clob) { implicit con =>
        SQL("SELECT c").as(scalar[Char].single) aka "parsed char" must_== 'a'
      }
  }

  "Column mapped as long" should {
    "be parsed from big decimal" in withQueryResult(bigDecimalList :+ bd) {
      implicit con =>
        SQL("SELECT bd").as(scalar[Long].single).aka("parsed long") must_== 34L
    }

    "be parsed from big integer" in withQueryResult(
      rowList1(classOf[java.math.BigInteger]) :+ bi) { implicit con =>
        SQL("SELECT bd").as(scalar[Long].single).
          aka("parsed long") must_== 1234L
      }

    "be parsed from long" in withQueryResult(longList :+ 23L) { implicit con =>
      SQL("SELECT l").as(scalar[Long].single) aka "parsed long" must_== 23L
    }

    "be parsed from integer" in withQueryResult(intList :+ 4) { implicit con =>
      SQL("SELECT i").as(scalar[Long].single) aka "parsed long" must_== 4L
    }

    "be parsed from short" in withQueryResult(shortList :+ 3.toShort) {
      implicit con =>
        SQL("SELECT s").as(scalar[Long].single).
          aka("parsed short") must_== 3L
    }

    "be parsed from byte" in withQueryResult(byteList :+ 4.toByte) {
      implicit con =>
        SQL("SELECT b").as(scalar[Long].single).
          aka("parsed byte") must_== 4L
    }

    "be parsed from false as 0" in withQueryResult(booleanList :+ false) {
      implicit con =>
        SQL("SELECT b").as(scalar[Long].single) aka "parsed long" must_== 0L
    }

    "be parsed from false as 1" in withQueryResult(booleanList :+ true) {
      implicit con =>
        SQL("SELECT b").as(scalar[Long].single) aka "parsed long" must_== 1L
    }

    val time = System.currentTimeMillis()
    val now = new java.sql.Date(time)
    trait TWrapper { def getTimestamp: java.sql.Timestamp }
    val tsw1 = new TWrapper {
      lazy val getTimestamp = new java.sql.Timestamp(time)
    }
    val tsNoRow = rowList1(classOf[TWrapper])

    "be parsed from date" in withQueryResult(dateList :+ now) { implicit con =>
      SQL"SELECT d".as(scalar[Long].single).
        aka("parsed long") must_== now.getTime
    }

    "be parsed from a timestamp wrapper" in withQueryResult(
      tsNoRow :+ tsw1) { implicit con =>
        SQL"SELECT d".as(scalar[Long].single).
          aka("parsed long") must_== now.getTime

      }

    "have convinence mapping function" in withQueryResult(
      longList.withLabel(1, "l") :+ 7L) { implicit con =>

        SQL("SELECT l").as(long("l").single) aka "parsed long" must_== 7L
      }
  }

  "Column mapped as integer" should {
    "be parsed from big decimal" in withQueryResult(bigDecimalList :+ bd) {
      implicit con =>
        SQL("SELECT bd").as(scalar[Int].single).
          aka("parsed integer") must_== 34L
    }

    "be parsed from big integer" in withQueryResult(
      rowList1(classOf[java.math.BigInteger]) :+ bi) { implicit con =>
        SQL("SELECT bd").as(scalar[Int].single).
          aka("parsed integer") must_== 1234L
      }

    "be parsed from long" in withQueryResult(longList :+ 23L) { implicit con =>
      SQL("SELECT l").as(scalar[Int].single) aka "parsed integer" must_== 23
    }

    "be parsed from integer" in withQueryResult(intList :+ 4) { implicit con =>
      SQL("SELECT i").as(scalar[Int].single) aka "parsed integer" must_== 4
    }

    "be parsed from short" in withQueryResult(shortList :+ 3.toShort) {
      implicit con =>
        SQL("SELECT s").as(scalar[Int].single).
          aka("parsed short") must_== 3
    }

    "be parsed from byte" in withQueryResult(byteList :+ 4.toByte) {
      implicit con =>
        SQL("SELECT b").as(scalar[Int].single).
          aka("parsed byte") must_== 4
    }

    "be parsed from false as 0" in withQueryResult(booleanList :+ false) {
      implicit con =>
        SQL("SELECT b").as(scalar[Int].single) aka "parsed integer" must_== 0L
    }

    "be parsed from false as 1" in withQueryResult(booleanList :+ true) {
      implicit con =>
        SQL("SELECT b").as(scalar[Int].single) aka "parsed integer" must_== 1L
    }

    "have convinence mapping function" in withQueryResult(
      rowList1(classOf[Int] -> "i") :+ 6) { implicit con =>

        SQL("SELECT i").as(int("i").single) aka "parsed integer" must_== 6
      }
  }

  "Column mapped as short" should {
    "be parsed from short" in withQueryResult(shortList :+ 3.toShort) {
      implicit con =>
        SQL("SELECT s").as(scalar[Short].single).
          aka("parsed short") must_== 3.toShort
    }

    "be parsed from byte" in withQueryResult(byteList :+ 4.toByte) {
      implicit con =>
        SQL("SELECT b").as(scalar[Short].single).
          aka("parsed short") must_== 4.toShort
    }

    "be parsed from false as 0" in withQueryResult(booleanList :+ false) {
      implicit con =>
        SQL("SELECT b").as(scalar[Short].single).
          aka("parsed short") must_== 0.toShort
    }

    "be parsed from false as 1" in withQueryResult(booleanList :+ true) {
      implicit con =>
        SQL("SELECT b").as(scalar[Short].single).
          aka("parsed short") must_== 1.toShort
    }

    "have convinence mapping function" in withQueryResult(
      rowList1(classOf[Short] -> "s") :+ 6.toShort) { implicit con =>

        SQL("SELECT s").as(short("s").single).
          aka("parsed short") must_== 6.toShort
      }
  }

  "Column mapped as byte" should {
    "be parsed from short" in withQueryResult(shortList :+ 3.toShort) {
      implicit con =>
        SQL("SELECT s").as(scalar[Byte].single).
          aka("parsed byte") must_== 3.toByte
    }

    "be parsed from byte" in withQueryResult(byteList :+ 4.toByte) {
      implicit con =>
        SQL("SELECT b").as(scalar[Byte].single).
          aka("parsed byte") must_== 4.toByte
    }

    "be parsed from false as 0" in withQueryResult(booleanList :+ false) {
      implicit con =>
        SQL("SELECT b").as(scalar[Byte].single).
          aka("parsed byte") must_== 0.toByte
    }

    "be parsed from false as 1" in withQueryResult(booleanList :+ true) {
      implicit con =>
        SQL("SELECT b").as(scalar[Byte].single).
          aka("parsed byte") must_== 1.toByte
    }

    "have convinence mapping function" in withQueryResult(
      rowList1(classOf[Byte] -> "b") :+ 6.toByte) { implicit con =>

        SQL("SELECT b").as(byte("b").single).
          aka("parsed byte") must_== 6.toByte
      }
  }

  "Column mapped as double" should {
    "be parsed from big decimal" in withQueryResult(bigDecimalList :+ bd) {
      implicit con =>
        SQL("SELECT bd").as(scalar[Double].single).
          aka("parsed double") must_== 34.5679d
    }

    "be parsed from double" in withQueryResult(doubleList :+ 1.2d) {
      implicit con =>
        SQL("SELECT d").as(scalar[Double].single).
          aka("parsed double") must_== 1.2d
    }

    "be parsed from float" in withQueryResult(floatList :+ 2.3f) {
      implicit con =>
        SQL("SELECT f").as(scalar[Double].single).
          aka("parsed double") must_== 2.3d
    }

    "be parsed from big integer" in withQueryResult(
      rowList1(classOf[java.math.BigInteger]) :+ bi) { implicit con =>
        SQL("SELECT bi").as(scalar[Double].single).
          aka("parsed double") must_== 1234d
      }

    "be parsed from integer" in withQueryResult(intList :+ 2) {
      implicit con =>
        SQL("SELECT i").as(scalar[Double].single) aka "parsed double" must_== 2d
    }

    "be parsed from short" in withQueryResult(shortList :+ 3.toShort) {
      implicit con =>
        SQL("SELECT s").as(scalar[Double].single).
          aka("parsed double") must_== 3.toShort
    }

    "be parsed from byte" in withQueryResult(byteList :+ 4.toByte) {
      implicit con =>
        SQL("SELECT s").as(scalar[Double].single).
          aka("parsed double") must_== 4.toByte
    }

    "have convinence mapping function" in withQueryResult(
      rowList1(classOf[Double] -> "d") :+ 1.2d) { implicit con =>

        SQL("SELECT d").as(double("d").single) aka "parsed double" must_== 1.2d
      }
  }

  "Column mapped as float" should {
    "be parsed from float" in withQueryResult(floatList :+ 2.3f) {
      implicit con =>
        SQL("SELECT f").as(scalar[Float].single).
          aka("parsed float") must_== 2.3f
    }

    "be parsed from big integer" in withQueryResult(
      rowList1(classOf[java.math.BigInteger]) :+ bi) { implicit con =>
        SQL("SELECT bi").as(scalar[Float].single).
          aka("parsed float") must_== 1234f
      }

    "be parsed from integer" in withQueryResult(intList :+ 2) {
      implicit con =>
        SQL("SELECT i").as(scalar[Float].single) aka "parsed float" must_== 2f
    }

    "be parsed from short" in withQueryResult(shortList :+ 3.toShort) {
      implicit con =>
        SQL("SELECT s").as(scalar[Float].single) aka "parsed float" must_== 3f
    }

    "be parsed from byte" in withQueryResult(byteList :+ 4.toByte) {
      implicit con =>
        SQL("SELECT s").as(scalar[Float].single) aka "parsed double" must_== 4f
    }

    "have convinence mapping function" in withQueryResult(
      rowList1(classOf[Float] -> "f") :+ 1.23f) { implicit con =>

        SQL("SELECT f").as(float("f").single) aka "parsed byte" must_== 1.23f
      }
  }

  "Column mapped as Java big decimal" should {
    "be parsed from big decimal" in withQueryResult(
      bigDecimalList :+ bd) { implicit con =>

        SQL("SELECT bd").as(scalar[java.math.BigDecimal].single).
          aka("parsed big decimal") must_== bd
      }

    "be parsed from big integer" in withQueryResult(
      rowList1(classOf[BigInteger]) :+ bi) { implicit con =>

        SQL("SELECT bi").as(scalar[java.math.BigDecimal].single).
          aka("parsed big decimal") must_== new java.math.BigDecimal(bi)
      }

    "be parsed from double" in withQueryResult(doubleList :+ 1.35d) {
      implicit con =>
        SQL("SELECT d").as(scalar[java.math.BigDecimal].single).
          aka("parsed big decimal") must_== java.math.BigDecimal.valueOf(1.35d)

    }

    "be parsed from float" in withQueryResult(floatList :+ 1.35f) {
      implicit con =>
        SQL("SELECT f").as(scalar[java.math.BigDecimal].single).
          aka("parsed big decimal") must_== java.math.BigDecimal.valueOf(1.35f)

    }

    "be parsed from long" in withQueryResult(longList :+ 5L) {
      implicit con =>
        SQL("SELECT l").as(scalar[java.math.BigDecimal].single).
          aka("parsed big decimal") must_== java.math.BigDecimal.valueOf(5L)

    }

    "be parsed from integer" in withQueryResult(longList :+ 6) {
      implicit con =>
        SQL("SELECT i").as(scalar[java.math.BigDecimal].single).
          aka("parsed big decimal") must_== java.math.BigDecimal.valueOf(6)

    }

    "be parsed from short" in withQueryResult(shortList :+ 7.toShort) {
      implicit con =>
        SQL("SELECT s").as(scalar[java.math.BigDecimal].single).
          aka("parsed big decimal") must_== java.math.BigDecimal.valueOf(7)
    }

    "be parsed from byte" in withQueryResult(byteList :+ 8.toByte) {
      implicit con =>
        SQL("SELECT s").as(scalar[java.math.BigDecimal].single).
          aka("parsed big decimal") must_== java.math.BigDecimal.valueOf(8)

    }
  }

  "Column mapped as Scala big decimal" should {
    "be parsed from big decimal" in withQueryResult(
      bigDecimalList :+ bd) { implicit con =>

        SQL("SELECT bd").as(scalar[BigDecimal].single).
          aka("parsed big decimal") must_== BigDecimal(bd)
      }

    "be parsed from double" in withQueryResult(doubleList :+ 1.35d) {
      implicit con =>
        SQL("SELECT d").as(scalar[BigDecimal].single).
          aka("parsed big decimal") must_== BigDecimal(1.35d)

    }

    "be parsed from float" in withQueryResult(floatList :+ 1.35f) {
      implicit con =>
        SQL("SELECT f").as(scalar[BigDecimal].single).
          aka("parsed big decimal") must_== BigDecimal(1.35f.toDouble)

    }

    "be parsed from long" in withQueryResult(longList :+ 5L) {
      implicit con =>
        SQL("SELECT l").as(scalar[BigDecimal].single).
          aka("parsed big decimal") must_== BigDecimal(5L)

    }

    "be parsed from integer" in withQueryResult(longList :+ 6) {
      implicit con =>
        SQL("SELECT i").as(scalar[BigDecimal].single).
          aka("parsed big decimal") must_== BigDecimal(6)

    }
  }

  "Column mapped as Java big integer" should {
    "be parsed from big decimal" in withQueryResult(
      rowList1(classOf[java.math.BigDecimal]) :+ bd) { implicit con =>
        // Useless as proper resultset won't return BigInteger

        SQL("SELECT bd").as(scalar[java.math.BigInteger].single).
          aka("parsed big decimal") must_== bd.toBigInteger
      }

    "be parsed from big integer" in withQueryResult(
      rowList1(classOf[java.math.BigInteger]) :+ bi) { implicit con =>
        // Useless as proper resultset won't return BigInteger

        SQL("SELECT bi").as(scalar[java.math.BigInteger].single).
          aka("parsed big integer") must_== bi
      }

    "be parsed from long" in withQueryResult(longList :+ 5L) {
      implicit con =>
        SQL("SELECT bi").as(scalar[java.math.BigInteger].single).
          aka("parsed long") must_== java.math.BigInteger.valueOf(5L)

    }

    "be parsed from int" in withQueryResult(intList :+ 2) {
      implicit con =>
        SQL("SELECT bi").as(scalar[java.math.BigInteger].single).
          aka("parsed int") must_== java.math.BigInteger.valueOf(2)

    }

    "be parsed from short" in withQueryResult(shortList :+ 3.toShort) {
      implicit con =>
        SQL("SELECT bi").as(scalar[java.math.BigInteger].single).
          aka("parsed short") must_== java.math.BigInteger.valueOf(3)
    }

    "be parsed from byte" in withQueryResult(byteList :+ 4.toByte) {
      implicit con =>
        SQL("SELECT bi").as(scalar[java.math.BigInteger].single).
          aka("parsed byte") must_== java.math.BigInteger.valueOf(4)
    }
  }

  "Column mapped as Scala big integer" should {
    "be parsed from big decimal" in withQueryResult(
      rowList1(classOf[java.math.BigDecimal]) :+ bd) { implicit con =>
        // Useless as proper resultset won't return BigInteger

        SQL("SELECT bd").as(scalar[BigInt].single).
          aka("parsed big decimal") must_== BigInt(bd.toBigInteger)
      }

    "be parsed from big integer" in withQueryResult(
      rowList1(classOf[java.math.BigInteger]) :+ bi) { implicit con =>
        // Useless as proper resultset won't return BigInteger

        SQL("SELECT bi").as(scalar[BigInt].single).
          aka("parsed big integer") must_== BigInt(bi)
      }

    "be parsed from long" in withQueryResult(longList :+ 5L) {
      implicit con =>
        SQL("SELECT bi").as(scalar[BigInt].single).
          aka("parsed long") must_== BigInt(5L)

    }

    "be parsed from int" in withQueryResult(intList :+ 2) {
      implicit con =>
        SQL("SELECT bi").as(scalar[BigInt].single).
          aka("parsed int") must_== BigInt(2)

    }

    "be parsed from short" in withQueryResult(shortList :+ 3.toShort) {
      implicit con =>
        SQL("SELECT bi").as(scalar[BigInt].single).
          aka("parsed short") must_== BigInt(3)
    }

    "be parsed from byte" in withQueryResult(byteList :+ 4.toByte) {
      implicit con =>
        SQL("SELECT bi").as(scalar[BigInt].single).
          aka("parsed byte") must_== BigInt(4)
    }
  }

  "Column mapped as date" should {
    val time = System.currentTimeMillis
    trait TWrapper { def getTimestamp: java.sql.Timestamp }
    val tsw1 = new TWrapper {
      lazy val getTimestamp = new java.sql.Timestamp(time)
    }
    trait TWrapper2 { def timestampValue: java.sql.Timestamp }
    val tsw2 = new TWrapper2 {
      lazy val timestampValue = new java.sql.Timestamp(time)
    }

    "be parsed from date" in withQueryResult(
      dateList :+ new java.sql.Date(time)) { implicit con =>
        SQL("SELECT d").as(scalar[java.util.Date].single).
          aka("parsed date") must_== new java.util.Date(time)
      }

    "be parsed from time" in withQueryResult(
      timeList :+ new java.sql.Time(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[java.util.Date].single).
          aka("parsed date") must beLike {
            case d => d.getTime aka "time" must_== time
          }
      }

    "be parsed from timestamp" in withQueryResult(
      timestampList :+ new java.sql.Timestamp(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[java.util.Date].single).
          aka("parsed date") must beLike {
            case d => d.getTime aka "time" must_== time
          }
      }

    "be parsed from numeric time" in withQueryResult(longList :+ time) {
      implicit con =>
        SQL("SELECT time").as(scalar[java.util.Date].single).
          aka("parsed date") must_== new java.util.Date(time)

    }

    "be parsed from a timestamp wrapper" >> {
      "with a not null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ tsw1) { implicit con =>
          SQL("SELECT time").as(scalar[java.util.Date].single).
            aka("parsed date") must_== new java.util.Date(time)

        }

      "with a null value" in withQueryResult(
        rowList1(classOf[TWrapper]) :+ null.asInstanceOf[TWrapper]) {
          implicit con =>
            SQL("SELECT time").as(scalar[java.util.Date].singleOpt).
              aka("parsed date") must beNone

        }
    }

    "be parsed from a Oracle timestamp wrapper" >> {
      "with a not null value" in withQueryResult(
        rowList1(classOf[TWrapper2]) :+ tsw2) { implicit con =>
          SQL("SELECT time").as(scalar[java.util.Date].single).
            aka("parsed date") must_== new java.util.Date(time)

        }

      "with a null value" in withQueryResult(
        rowList1(classOf[TWrapper2]) :+ null.asInstanceOf[TWrapper2]) {
          implicit con =>
            SQL("SELECT time").as(scalar[java.util.Date].singleOpt).
              aka("parsed date") must beNone

        }
    }
  }

  "Column mapped as UUID" should {
    val uuid = UUID.randomUUID
    val uuidStr = uuid.toString

    "be pased from a UUID" in withQueryResult(rowList1(classOf[UUID]) :+ uuid) { implicit con =>
      SQL("SELECT uuid").as(scalar[UUID].single).
        aka("parsed uuid") must_== uuid
    }

    "be parsed from a valid string" in withQueryResult(stringList :+ uuidStr) { implicit con =>
      SQL("SELECT uuid").as(scalar[UUID].single).
        aka("parsed uuid") must_== uuid
    }

    "not be parsed from an invalid string" in withQueryResult(stringList :+ Random.nextString(36)) { implicit con =>
      SQL("SELECT uuid").as(scalar[UUID].single).
        aka("parsed uuid") must throwA[Exception](message =
          "TypeDoesNotMatch\\(Cannot convert.* to UUID")
    }

    "not be mapped from an unknown type" in withQueryResult(booleanList :+ false) { implicit con =>
      SQL("SELECT uuid").as(scalar[UUID].single).
        aka("parsed uuid") must throwA[Exception](message =
          "TypeDoesNotMatch\\(Cannot convert.*Boolean to UUID")
    }
  }

  "Column mapped as array" should {
    val sqlArray = ImmutableArray.
      getInstance(classOf[String], Array("aB", "Cd", "EF"))

    "be parsed from SQL array" >> {
      "when not NULL" in withQueryResult(
        rowList1(classOf[SqlArray]) :+ sqlArray) { implicit con =>

          SQL"SELECT a".as(scalar[Array[String]].single).
            aka("parsed array") mustEqual Array("aB", "Cd", "EF")
        }

      "when NULL" in withQueryResult(rowList1(
        classOf[SqlArray]) :+ null.asInstanceOf[SqlArray]) { implicit con =>

        SQL"SELECT a".as(scalar[Array[String]].?.single).
          aka("optional array") must beNone
      }
    }

    val idArray = Array(java.util.UUID.randomUUID, java.util.UUID.randomUUID)
    "be parsed from raw array" in withQueryResult(
      rowList1(classOf[Array[_]]) :+ idArray) { implicit con =>
        SQL"SELECT ids".as(scalar[Array[java.util.UUID]].single).
          aka("parsed array") mustEqual idArray
      }

    val jset = {
      val l = new java.util.TreeSet[String](); l.add("CD"); l.add("EF"); l
    }
    "be parsed from Java iterable" in withQueryResult(
      rowList1(classOf[java.util.Collection[String]]) :+ jset) { implicit con =>
        SQL"SELECT it".as(scalar[Array[String]].singleOpt).
          aka("parsed iterable") must beSome.which(_ must_== Array("CD", "EF"))
      }

    "have convinience mapping function" in withQueryResult(
      rowList1(classOf[SqlArray]).withLabels(1 -> "a") :+ sqlArray) {
        implicit con =>

          SQL"SELECT a".as(SqlParser.array[String]("a").single).
            aka("parsed array") mustEqual Array("aB", "Cd", "EF")
      }

    "not be parsed from array with invalid component type" in withQueryResult(
      rowList1(classOf[SqlArray]) :+ acolyte.jdbc.ImmutableArray.getInstance(
        classOf[java.sql.Date], Array(new java.sql.Date(1L),
          new java.sql.Date(2L)))) { implicit con =>

        SQL"SELECT a".as(scalar[Array[String]].single).
          aka("parsing") must throwA[Exception](message =
            "TypeDoesNotMatch\\(Cannot convert ImmutableArray")

      }

    "not be parsed from float" in withQueryResult(floatList :+ 2f) {
      implicit con =>
        SQL"SELECT a".as(scalar[Array[String]].single).
          aka("parsing") must throwA[Exception](message =
            "TypeDoesNotMatch\\(Cannot convert.* to array")
    }

    "be parsed from SQL array with integer to big integer convertion" in {
      withQueryResult(rowList1(classOf[SqlArray]) :+ ImmutableArray.getInstance(
        classOf[Integer], Array[Integer](1, 3))) { implicit con =>

        SQL"SELECT a".as(scalar[Array[BigInteger]].single).
          aka("parsed array") mustEqual Array(
            BigInteger.valueOf(1), BigInteger.valueOf(3))
      }
    }
  }

  "Column mapped as list" should {
    val sqlArray = ImmutableArray.
      getInstance(classOf[String], Array("aB", "Cd", "EF"))

    "be parsed from SQL array" in withQueryResult(
      rowList1(classOf[SqlArray]) :+ sqlArray) { implicit con =>

        SQL"SELECT a".as(scalar[List[String]].single).
          aka("parsed list") mustEqual List("aB", "Cd", "EF")
      }

    val idArray = Array(java.util.UUID.randomUUID, java.util.UUID.randomUUID)
    "be parsed from raw array" in withQueryResult(
      rowList1(classOf[Array[_]]) :+ idArray) { implicit con =>

        SQL"SELECT ids".as(scalar[List[java.util.UUID]].single).
          aka("parsed array") mustEqual idArray.toList
      }

    val jlist = {
      val l = new java.util.ArrayList[String](); l.add("A"); l.add("B"); l
    }
    "be parsed from Java iterable" in withQueryResult(
      rowList1(classOf[java.lang.Iterable[String]]) :+ jlist) { implicit con =>
        SQL"SELECT it".as(SqlParser.list[String](1).singleOpt).
          aka("parsed iterable") must beSome.which(_ must_== List("A", "B"))
      }

    "have convinience mapping function" in withQueryResult(
      rowList1(classOf[SqlArray]).withLabels(1 -> "a") :+ sqlArray) {
        implicit con =>
          SQL"SELECT a".as(SqlParser.list[String]("a").single).
            aka("parsed array") mustEqual List("aB", "Cd", "EF")
      }

    "not be parsed from SQL array with invalid component type" in withQueryResult(
      rowList1(classOf[SqlArray]) :+ acolyte.jdbc.ImmutableArray.getInstance(
        classOf[java.sql.Date], Array(new java.sql.Date(1L),
          new java.sql.Date(2L)))) { implicit con =>

        SQL"SELECT a".as(scalar[List[String]].single).
          aka("parsing") must throwA[Exception](message =
            "TypeDoesNotMatch\\(Cannot convert ImmutableArray")

      }

    "not be parsed from float" in withQueryResult(floatList :+ 2f) {
      implicit con =>
        SQL"SELECT a".as(scalar[List[String]].single).
          aka("parsing") must throwA[Exception](message =
            "TypeDoesNotMatch\\(Cannot convert.* to list")
    }

    "be parsed from SQL array with integer to big integer convertion" in {
      withQueryResult(rowList1(classOf[SqlArray]) :+ ImmutableArray.getInstance(
        classOf[Integer], Array[Integer](1, 3))) { implicit con =>

        SQL"SELECT a".as(scalar[List[BigInteger]].single).
          aka("parsed list") mustEqual List(
            BigInteger.valueOf(1), BigInteger.valueOf(3))
      }
    }
  }
}

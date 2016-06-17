package anorm

import SqlParser.{ bool, str, int, long, get }

import acolyte.jdbc.RowLists._
import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.Implicits._

class FunctionAdapterSpec extends org.specs2.mutable.Specification {
  "Function flattener" title

  "Single column" should {
    "be applied with parser function" in withQueryResult(intList :+ 123) {
      implicit c =>
        SQL("SELECT * FROM test").as(
          int(1) map (SqlParser.to(_.toString)) single) must_== "123"
    }
  }

  "Raw tuple-like" should {
    "be applied with 2 columns to Function2" in {
      def foo(a: String, b: Int) = "Fn2"
      val schema = rowList2(classOf[String] -> "A", classOf[Int] -> "B")

      withQueryResult(schema :+ ("A", 2)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") map (SqlParser.to(foo _)) single).
          aka("function result") must_== "Fn2"

      }
    }

    "be applied with 3 columns to Function3" in {
      case class Foo(a: String, b: Int, c: Long)
      val schema = rowList3(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C")

      withQueryResult(schema :+ ("A", 2, 3L)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") map (
            SqlParser.to(Foo.apply _)) single).
          aka("function result") must_== Foo("A", 2, 3L)

      }
    }

    "be applied with 4 columns to Function4" in {
      def foo(a: String, b: Int, c: Long, d: Double) = "Fn4"
      val schema = rowList4(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") map (SqlParser.to(foo _)) single).
          aka("function result") must_== "Fn4"

      }
    }

    "be applied with 5 columns to Function5" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short) = "Fn5"
      val schema = rowList5(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") map (SqlParser.to(foo _)) single).
          aka("function result") must_== "Fn5"

      }
    }

    "be applied with 6 columns to Function6" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte) = "Fn6"
      val schema = rowList6(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") map (SqlParser.to(foo _)) single).
          aka("function result") must_== "Fn6"

      }
    }

    "be applied with 7 columns to Function7" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean) = "Fn7"
      val schema = rowList7(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn7"

      }
    }

    "be applied with 8 columns to Function8" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String) = "Fn8"
      val schema = rowList8(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B")) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn8"

      }
    }

    "be applied with 9 columns to Function9" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int) = "Fn9"
      val schema = rowList9(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn9"

      }
    }

    "be applied with 10 columns to Function10" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long) = "Fn10"
      val schema = rowList10(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn10"

      }
    }

    "be applied with 11 columns to Function11" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long, k: Double) = "Fn11"
      val schema = rowList11(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J", classOf[Double] -> "K")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l, 5.67d)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") ~ get[Double]("K") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn11"

      }
    }

    "be applied with 12 columns to Function12" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long, k: Double, l: Short) = "Fn12"
      val schema = rowList12(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J", classOf[Double] -> "K", classOf[Short] -> "L")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l, 5.67d, 10.toShort)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") ~ get[Double]("K") ~ get[Short]("L") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn12"

      }
    }

    "be applied with 13 columns to Function13" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long, k: Double, l: Short, m: Byte) = "Fn13"
      val schema = rowList13(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J", classOf[Double] -> "K", classOf[Short] -> "L", classOf[Byte] -> "M")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l, 5.67d, 10.toShort, 11.toByte)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") ~ get[Double]("K") ~ get[Short]("L") ~ get[Byte]("M") map (SqlParser.to(foo _)) single).
          aka("function result") must_== "Fn13"

      }
    }

    "be applied with 14 columns to Function14" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long, k: Double, l: Short, m: Byte, n: Boolean) = "Fn14"
      val schema = rowList14(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J", classOf[Double] -> "K", classOf[Short] -> "L", classOf[Byte] -> "M", classOf[Boolean] -> "N")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l, 5.67d, 10.toShort, 11.toByte, false)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") ~ get[Double]("K") ~ get[Short]("L") ~ get[Byte]("M") ~ bool("N") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn14"

      }
    }

    "be applied with 15 columns to Function15" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long, k: Double, l: Short, m: Byte, n: Boolean, o: String) = "Fn15"
      val schema = rowList15(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J", classOf[Double] -> "K", classOf[Short] -> "L", classOf[Byte] -> "M", classOf[Boolean] -> "N", classOf[String] -> "O")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l, 5.67d, 10.toShort, 11.toByte, false, "C")) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") ~ get[Double]("K") ~ get[Short]("L") ~ get[Byte]("M") ~ bool("N") ~ str("O") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn15"

      }
    }

    "be applied with 16 columns to Function16" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long, k: Double, l: Short, m: Byte, n: Boolean, o: String, p: Int) = "Fn16"
      val schema = rowList16(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J", classOf[Double] -> "K", classOf[Short] -> "L", classOf[Byte] -> "M", classOf[Boolean] -> "N", classOf[String] -> "O", classOf[Int] -> "P")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l, 5.67d, 10.toShort, 11.toByte, false, "C", 3)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") ~ get[Double]("K") ~ get[Short]("L") ~ get[Byte]("M") ~ bool("N") ~ str("O") ~ int("P") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn16"

      }
    }

    "be applied with 17 columns to Function17" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long, k: Double, l: Short, m: Byte, n: Boolean, o: String, p: Int, q: Long) = "Fn17"
      val schema = rowList17(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J", classOf[Double] -> "K", classOf[Short] -> "L", classOf[Byte] -> "M", classOf[Boolean] -> "N", classOf[String] -> "O", classOf[Int] -> "P", classOf[Long] -> "Q")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l, 5.67d, 10.toShort, 11.toByte, false, "C", 3, 4l)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") ~ get[Double]("K") ~ get[Short]("L") ~ get[Byte]("M") ~ bool("N") ~ str("O") ~ int("P") ~ long("Q") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn17"

      }
    }

    "be applied with 18 columns to Function18" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long, k: Double, l: Short, m: Byte, n: Boolean, o: String, p: Int, q: Long, r: Double) = "Fn18"
      val schema = rowList18(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J", classOf[Double] -> "K", classOf[Short] -> "L", classOf[Byte] -> "M", classOf[Boolean] -> "N", classOf[String] -> "O", classOf[Int] -> "P", classOf[Long] -> "Q", classOf[Double] -> "R")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l, 5.67d, 10.toShort, 11.toByte, false, "C", 3, 4l, 5.678d)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") ~ get[Double]("K") ~ get[Short]("L") ~ get[Byte]("M") ~ bool("N") ~ str("O") ~ int("P") ~ long("Q") ~ get[Double]("R") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn18"

      }
    }

    "be applied with 19 columns to Function19" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long, k: Double, l: Short, m: Byte, n: Boolean, o: String, p: Int, q: Long, r: Double, s: Short) = "Fn19"
      val schema = rowList19(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J", classOf[Double] -> "K", classOf[Short] -> "L", classOf[Byte] -> "M", classOf[Boolean] -> "N", classOf[String] -> "O", classOf[Int] -> "P", classOf[Long] -> "Q", classOf[Double] -> "R", classOf[Short] -> "S")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l, 5.67d, 10.toShort, 11.toByte, false, "C", 3, 4l, 5.678d, 16.toShort)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") ~ get[Double]("K") ~ get[Short]("L") ~ get[Byte]("M") ~ bool("N") ~ str("O") ~ int("P") ~ long("Q") ~ get[Double]("R") ~ get[Short]("S") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn19"

      }
    }

    "be applied with 20 columns to Function20" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long, k: Double, l: Short, m: Byte, n: Boolean, o: String, p: Int, q: Long, r: Double, s: Short, t: String) = "Fn20"
      val schema = rowList20(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J", classOf[Double] -> "K", classOf[Short] -> "L", classOf[Byte] -> "M", classOf[Boolean] -> "N", classOf[String] -> "O", classOf[Int] -> "P", classOf[Long] -> "Q", classOf[Double] -> "R", classOf[Short] -> "S", classOf[String] -> "T")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l, 5.67d, 10.toShort, 11.toByte, false, "C", 3, 4l, 5.678d, 16.toShort, "D")) { implicit c =>
        SQL("SELECT * FROM test").as(str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") ~ get[Double]("K") ~ get[Short]("L") ~ get[Byte]("M") ~ bool("N") ~ str("O") ~ int("P") ~ long("Q") ~ get[Double]("R") ~ get[Short]("S") ~ str("T") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn20"

      }
    }

    "be applied with 21 columns to Function21" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long, k: Double, l: Short, m: Byte, n: Boolean, o: String, p: Int, q: Long, r: Double, s: Short, t: String, u: Int) = "Fn21"
      val schema = rowList21(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J", classOf[Double] -> "K", classOf[Short] -> "L", classOf[Byte] -> "M", classOf[Boolean] -> "N", classOf[String] -> "O", classOf[Int] -> "P", classOf[Long] -> "Q", classOf[Double] -> "R", classOf[Short] -> "S", classOf[String] -> "T", classOf[Int] -> "U")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l, 5.67d, 10.toShort, 11.toByte, false, "C", 3, 4l, 5.678d, 16.toShort, "D", 4)) { implicit c =>
        SQL("SELECT * FROM test").as(str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") ~ get[Double]("K") ~ get[Short]("L") ~ get[Byte]("M") ~ bool("N") ~ str("O") ~ int("P") ~ long("Q") ~ get[Double]("R") ~ get[Short]("S") ~ str("T") ~ int("U") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn21"

      }
    }

    "be applied with 22 columns to Function22" in {
      def foo(a: String, b: Int, c: Long, d: Double, e: Short, f: Byte, g: Boolean, h: String, i: Int, j: Long, k: Double, l: Short, m: Byte, n: Boolean, o: String, p: Int, q: Long, r: Double, s: Short, t: String, u: Int, v: Long) = "Fn22"
      val schema = rowList22(classOf[String] -> "A", classOf[Int] -> "B", classOf[Long] -> "C", classOf[Double] -> "D", classOf[Short] -> "E", classOf[Byte] -> "F", classOf[Boolean] -> "G", classOf[String] -> "H", classOf[Int] -> "I", classOf[Long] -> "J", classOf[Double] -> "K", classOf[Short] -> "L", classOf[Byte] -> "M", classOf[Boolean] -> "N", classOf[String] -> "O", classOf[Int] -> "P", classOf[Long] -> "Q", classOf[Double] -> "R", classOf[Short] -> "S", classOf[String] -> "T", classOf[Int] -> "U", classOf[Long] -> "V")

      withQueryResult(schema :+ ("A", 2, 3l, 4.56d, 9.toShort, 10.toByte, true, "B", 3, 4l, 5.67d, 10.toShort, 11.toByte, false, "C", 3, 4l, 5.678d, 16.toShort, "D", 4, 5l)) { implicit c =>
        SQL("SELECT * FROM test").as(
          str("A") ~ int("B") ~ long("C") ~ get[Double]("D") ~ get[Short]("E") ~ get[Byte]("F") ~ bool("G") ~ str("H") ~ int("I") ~ long("J") ~ get[Double]("K") ~ get[Short]("L") ~ get[Byte]("M") ~ bool("N") ~ str("O") ~ int("P") ~ long("Q") ~ get[Double]("R") ~ get[Short]("S") ~ str("T") ~ int("U") ~ long("V") map (SqlParser.to(foo _)) single).aka("function result") must_== "Fn22"

      }
    }
  }
}

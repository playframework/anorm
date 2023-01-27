/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.enumeratum

import scala.util.control.NonFatal

import acolyte.jdbc.{ RowList, RowLists }
import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.Implicits._

import anorm.{ AnormException, SQL, SqlParser, TypeDoesNotMatch }

import SqlParser.scalar

final class EnumColumnSpec extends org.specs2.mutable.Specification {
  "Enum column" title

  "Sensitive enum" should {
    "be successfully parsed as Column" >> {
      def spec[T <: Dummy](repr: String, expected: T) =
        repr in withQueryResult(RowLists.stringList :+ repr) { implicit con =>
          SQL("SELECT v").as(scalar[Dummy].single) must_=== expected
        }

      spec("A", Dummy.A)
      spec("B", Dummy.B)
      spec("c", Dummy.c)
    }

    "not be parsed as Column from invalid String representation" >> {
      def spec(title: String, repr: String) =
        title in withQueryResult(RowLists.stringList :+ repr) { implicit con =>
          SQL("SELECT v").as(scalar[Dummy].single) must throwA[Exception].like {
            case NonFatal(cause) =>
              cause must_=== AnormException(TypeDoesNotMatch(s"Invalid value: $repr").message)
          }
        }

      spec("a (!= A as sensitive)", "a")
      spec("b (!= B as sensitive)", "b")
      spec("C (!= c as sensitive)", "C")
    }

    "not be parsed as Column from non-String values" >> {
      def spec(tpe: String, rowList: RowList[_]) =
        tpe in withQueryResult(rowList) { implicit con =>
          SQL("SELECT v").as(scalar[Dummy].single) must throwA[Exception].like {
            case NonFatal(cause) =>
              cause must_=== AnormException(
                TypeDoesNotMatch(s"Column '.null' expected to be String; Found $tpe").message
              )
          }
        }

      spec("float", RowLists.floatList :+ 0.1F)
      spec("int", RowLists.intList :+ 1)
    }
  }

  "Insensitive enum" should {
    "successfully parsed as Column" >> {
      def spec[T <: InsensitiveDummy](repr: String, expected: T) =
        repr in withQueryResult(RowLists.stringList :+ repr) { implicit con =>
          SQL("SELECT v").as(scalar[InsensitiveDummy].single) must_=== expected
        }

      spec("A", InsensitiveDummy.A)
      spec("a", InsensitiveDummy.A)

      spec("B", InsensitiveDummy.B)
      spec("b", InsensitiveDummy.B)

      spec("C", InsensitiveDummy.c)
      spec("c", InsensitiveDummy.c)
    }

    "not be parsed as Column from non-String values" >> {
      def spec(tpe: String, rowList: RowList[_]) =
        tpe in withQueryResult(rowList) { implicit con =>
          SQL("SELECT v").as(scalar[InsensitiveDummy].single).aka("result") must throwA[Exception].like {
            case NonFatal(cause) =>
              cause must_=== AnormException(
                TypeDoesNotMatch(s"Column '.null' expected to be String; Found $tpe").message
              )
          }
        }

      spec("float", RowLists.floatList :+ 0.1F)
      spec("int", RowLists.intList :+ 1)
    }
  }

  "Lowercase enum" should {
    "successfully parsed as Column" >> {
      def spec[T <: LowercaseDummy](repr: String, expected: T) =
        repr in withQueryResult(RowLists.stringList :+ repr) { implicit con =>
          SQL("SELECT v").as(scalar[LowercaseDummy].single) must_=== expected
        }

      spec("apple", LowercaseDummy.Apple)
      spec("banana", LowercaseDummy.Banana)
      spec("cherry", LowercaseDummy.Cherry)
    }

    "not be parsed as Column from invalid String representation" >> {
      def spec(title: String, repr: String) =
        title in withQueryResult(RowLists.stringList :+ repr) { implicit con =>
          SQL("SELECT v").as(scalar[LowercaseDummy].single).aka("result") must throwA[Exception].like {
            case NonFatal(cause) =>
              cause must_=== AnormException(TypeDoesNotMatch(s"Invalid value: $repr").message)
          }
        }

      spec("Apple (!= apple as lowercase)", "Apple")
      spec("BANANA (!= banana as lowercase)", "BANANA")
      spec("Cherry (!= cherry as lowercase)", "Cherry")
    }

    "not be parsed as Column from non-String values" >> {
      def spec(tpe: String, rowList: RowList[_]) =
        tpe in withQueryResult(rowList) { implicit con =>
          SQL("SELECT v").as(scalar[LowercaseDummy].single).aka("result") must throwA[Exception].like {
            case NonFatal(cause) =>
              cause must_=== AnormException(
                TypeDoesNotMatch(s"Column '.null' expected to be String; Found $tpe").message
              )
          }
        }

      spec("float", RowLists.floatList :+ 0.1F)
      spec("int", RowLists.intList :+ 1)
    }
  }

  "Uppercase enum" should {
    "successfully parsed as Column" >> {
      def spec[T <: UppercaseDummy](repr: String, expected: T) =
        repr in withQueryResult(RowLists.stringList :+ repr) { implicit con =>
          SQL("SELECT v").as(scalar[UppercaseDummy].single) must_=== expected
        }

      spec("APPLE", UppercaseDummy.Apple)
      spec("BANANA", UppercaseDummy.Banana)
      spec("CHERRY", UppercaseDummy.Cherry)
    }

    "not be parsed as Column from invalid String representation" >> {
      def spec(title: String, repr: String) =
        title in withQueryResult(RowLists.stringList :+ repr) { implicit con =>
          SQL("SELECT v").as(scalar[UppercaseDummy].single).aka("result") must throwA[Exception].like {
            case NonFatal(cause) =>
              cause must_=== AnormException(TypeDoesNotMatch(s"Invalid value: $repr").message)
          }
        }

      spec("Apple (!= APPLE as uppercase)", "Apple")
      spec("banana (!= BANANA as uppercase)", "banana")
      spec("cherry (!= CHERRY as uppercase)", "Cherry")
    }

    "not be parsed as Column from non-String values" >> {
      def spec(tpe: String, rowList: RowList[_]) =
        tpe in withQueryResult(rowList) { implicit con =>
          SQL("SELECT v").as(scalar[UppercaseDummy].single).aka("result") must throwA[Exception].like {
            case NonFatal(cause) =>
              cause must_=== AnormException(
                TypeDoesNotMatch(s"Column '.null' expected to be String; Found $tpe").message
              )
          }
        }

      spec("float", RowLists.floatList :+ 0.1F)
      spec("int", RowLists.intList :+ 1)
    }
  }
}

/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.enumeratum.values

import scala.util.control.NonFatal

import acolyte.jdbc.{ RowList, RowLists }
import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.Implicits._

import anorm.{ AnormException, SQL, SqlParser, TypeDoesNotMatch }

import SqlParser.scalar

final class ValueEnumColumnSpec extends org.specs2.mutable.Specification {
  "ValueEnum column" title

  "ValueEnum" should {
    "be successfully parsed as Column" >> {
      def spec[T <: Drink](repr: Short, expected: T) =
        repr.toString in withQueryResult(RowLists.shortList :+ repr) { implicit con =>
          SQL("SELECT v").as(scalar[Drink].single) mustEqual expected
        }

      spec(1, Drink.OrangeJuice)
      spec(2, Drink.AppleJuice)
      spec(3, Drink.Cola)
      spec(4, Drink.Beer)
    }

    "not be parsed as Column from invalid Short representation" in {
      withQueryResult(RowLists.shortList :+ 0.toShort) { implicit con =>
        SQL("SELECT v").as(scalar[Drink].single) must throwA[Exception].like {
          case NonFatal(cause) =>
            cause mustEqual AnormException(TypeDoesNotMatch(s"Invalid value: 0").message)
        }
      }
    }

    "not be parsed as Column from non-Short values" >> {
      def spec(tpe: String, rowList: RowList[_]) =
        tpe in withQueryResult(rowList) { implicit con =>
          SQL("SELECT v").as(scalar[Drink].single) must throwA[AnormException]
        }

      spec("float", RowLists.floatList :+ 0.12F)
      spec("String", RowLists.stringList :+ "foo")
    }
  }
}

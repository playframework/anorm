package anorm

import acolyte.jdbc.RowLists.rowList1
import acolyte.jdbc.Implicits._

class CursorSpec extends org.specs2.mutable.Specification {
  "Cursor" title

  "Cursor" should {
    "not be returned when there is no result" in {
      Cursor(stringList.resultSet, ColumnAliaser.empty) aka "cursor" must beNone
    }

    "be returned for one row" in {
      Cursor(stringList :+ "A" resultSet, ColumnAliaser.empty).
        aka("cursor") must beSome.which { cur =>
          cur.row[String]("str") aka "row" must_== "A" and (
            cur.next aka "after first" must beNone)
        }
    }

    "be return for three rows" in {
      Cursor(stringList :+ "red" :+ "green" :+ "blue" resultSet,
        ColumnAliaser.empty) aka "cursor" must beSome.which { first =>
          first.row[String]("str") aka "row #1" must_== "red" and (
            first.next aka "after first" must beSome.which { snd =>
              snd.row[String]("str") aka "row #2" must_== "green" and (
                snd.next aka "after second" must beSome.which { third =>
                  third.row[String]("str") aka "row #1" must_== "blue" and (
                    third.next aka "after third" must beNone)
                })
            })
        }
    }

    "match pattern" in {
      Cursor(stringList :+ "Foo" :+ "Bar" resultSet, ColumnAliaser.empty).
        aka("cursor") must beSome[Cursor].like {
          case Cursor(row1, b) => row1[String](1) must_== "Foo" and (
            b must beSome[Cursor].like {
              case Cursor(row2, None) => row2[String](1) must_== "Bar"
            })
        }
    }
  }

  def stringList = rowList1(classOf[String] -> "str")
}

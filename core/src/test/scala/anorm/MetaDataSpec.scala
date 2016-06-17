package anorm

import acolyte.jdbc.RowLists.rowList3
import acolyte.jdbc.Implicits._

class MetaDataSpec extends org.specs2.mutable.Specification {
  "Meta-data" title

  "Meta-data" should {
    "support column aliases" in {
      val item1 = MetaDataItem(ColumnName(
        "TEST1.FOO", Some("ALI")), true, "java.lang.String")

      val item2 = MetaDataItem(ColumnName(
        "TEST1.FOO", Some("FOO")), true, "java.lang.String")

      val item3 = MetaDataItem(ColumnName(
        "TEST1.FOO", Some("IAS")), true, "java.lang.String")

      val meta1 = MetaData(List(item1, item2, item3))

      meta1.get("ALI") aka "ALI" must beSome(item1) and (
        meta1.get("FOO") aka "FOO" must beSome(item2)) and (
          meta1.get("IAS") aka "IAS" must beSome(item3))
    }

    "be parsed from resultset" >> {
      import scala.language.existentials
      @inline def rs = (fooBarTable :+ (1L, "lorem", 3)).getRowList.resultSet()

      val item1 = MetaDataItem(ColumnName(
        ".id", Some("id")), false, "long")

      val item2 = MetaDataItem(ColumnName(
        ".foo", Some("foo")), false, "java.lang.String")

      val item3 = MetaDataItem(ColumnName(
        ".bar", Some("bar")), false, "int")

      "without aliaser" in {
        MetaData.parse(rs, ColumnAliaser.empty).
          aka("metadata") must_== MetaData(List(item1, item2, item3))
      }

      "with partial aliaser" in {
        val itemA = item1.copy(column =
          item1.column.copy(alias = Some("my_id")))
        val itemB = item2.copy(column =
          item2.column.copy(alias = Some("prefix.foo")))

        MetaData.parse(rs, ColumnAliaser({
          case (1, cn) => "my_id"
          case (_, ColumnName(".foo", _)) => "prefix.foo"
        })) must_== MetaData(List(itemA, itemB, item3))
      }

      "with positional aliaser" in {
        val itemB = item2.copy(column =
          item2.column.copy(alias = Some("prefix.foo")))
        val itemC = item3.copy(column =
          item3.column.copy(alias = Some("barbar")))

        MetaData.parse(rs, ColumnAliaser.perPositions((2 to 3).toSet) {
          case (2, _) => "prefix.foo"
          case _ => "barbar"
        }) must_== MetaData(List(item1, itemB, itemC))
      }

      "with pattern aliaser #1" in {
        val itemB = item2.copy(column =
          item2.column.copy(alias = Some("prefix.foo")))
        val itemC = item3.copy(column =
          item3.column.copy(alias = Some("prefix.bar")))

        MetaData.parse(rs, ColumnAliaser.
          withPattern((2 to 3).toSet, "prefix.")) must_== MetaData(
          List(item1, itemB, itemC))
      }

      "with pattern aliaser #2" in {
        val itemB = item2.copy(column =
          item2.column.copy(alias = Some("prefix.foo")))

        MetaData.parse(rs, ColumnAliaser.
          withPattern1("prefix.")(2, 2)) must_== MetaData(
          List(item1, itemB, item3))
      }
    }
  }

  lazy val fooBarTable = rowList3(
    classOf[Long] -> "id", classOf[String] -> "foo", classOf[Int] -> "bar")

}

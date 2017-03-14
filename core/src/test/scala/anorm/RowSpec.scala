package anorm

import scala.util.Try

import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.RowLists.{ rowList2, rowList1, stringList }
import acolyte.jdbc.Implicits._

class RowSpec extends org.specs2.mutable.Specification {
  "Row" title

  "List of metadata items" should {
    "include all columns" in withQueryResult(rowList2(
      classOf[String] -> "foo", classOf[Int] -> "bar") :+ ("row1", 100)) {
      implicit c =>
        SQL("SELECT * FROM test").map(_.metaDataItems).single.
          aka("metadata item list") must_== List(
            MetaDataItem(ColumnName(".foo", Some("foo")), false, "java.lang.String"),
            MetaDataItem(ColumnName(".bar", Some("bar")), false, "int"))
    }
  }

  "List of column values" should {
    "be expected one" in withQueryResult(rowList2(
      classOf[String] -> "foo", classOf[Int] -> "bar") :+ ("row1", 100)) {
      implicit c =>
        SQL("SELECT * FROM test").as(RowParser(r => Success(r.asList)).single).
          aka("column list") must_== List("row1", 100)
    }

    "keep null if not nullable" in withQueryResult(stringList :+ null) {
      implicit c =>
        SQL("SELECT 1").as(RowParser(r => Success(r.asList)).single).
          aka("column list") must_== List(null)

    }

    "turn null into None if nullable" in withQueryResult(
      stringList.withNullable(1, true) :+ null) { implicit c =>
        SQL("SELECT 1").as(RowParser(r => Success(r.asList)).single).
          aka("column list") must_== List(None)

      }

    "turn value into Some(X) if nullable" in withQueryResult(
      stringList.withNullable(1, true) :+ "str") { implicit c =>
        SQL("SELECT 1").as(RowParser(r => Success(r.asList)).single).
          aka("column list") must_== List(Some("str"))

      }

    "find the second lowercase alias when duplicate qualified column names exist" in {
      case class ResultRow(metaData: MetaData, data: List[Any]) extends Row
      val meta1 = MetaDataItem(ColumnName("table.id", Some("first_id")), false, "java.lang.Integer")
      val meta2 = MetaDataItem(ColumnName("table.id", Some("second_id")), false, "java.lang.Integer")
      val metaData = MetaData(List(meta1, meta2))
      val row = ResultRow(metaData, List(1, 2))
      row.get("second_id").toEither must beRight((2, meta2))
    }

    "find the correct mixed-case alias when duplicate qualified column names exist" in {
      case class ResultRow(metaData: MetaData, data: List[Any]) extends Row
      val meta1 = MetaDataItem(ColumnName("data.name", Some("WrongAlias")), false, "java.lang.String")
      val meta2 = MetaDataItem(ColumnName("data.name", Some("CorrectAlias")), false, "java.lang.String")
      val metaData = MetaData(List(meta1, meta2))
      val row = ResultRow(metaData, List("IncorrectString", "CorrectString"))
      row.get("CorrectAlias").toEither must beRight(("CorrectString", meta2))
    }
  }

  "Column dictionary" should {
    "be expected one" in withQueryResult(rowList2(
      classOf[String] -> "foo", classOf[Int] -> "bar") :+ ("row1", 100)) {
      implicit c =>
        SQL("SELECT * FROM test").as(RowParser(r => Success(r.asMap)).single).
          aka("column map") must_== Map(".foo" -> "row1", ".bar" -> 100)

    }

    "keep null if not nullable" in withQueryResult(
      rowList1(classOf[String] -> "foo") :+ null) { implicit c =>
        SQL("SELECT 1").as(RowParser(r => Success(r.asMap)).single).
          aka("column map") must_== Map(".foo" -> null)

      }

    "turn null into None if nullable" in withQueryResult(
      rowList1(classOf[String] -> "foo").withNullable(1, true) :+ null) {
        implicit c =>
          SQL("SELECT 1").as(RowParser(r => Success(r.asMap)).single).
            aka("column map") must_== Map(".foo" -> None)

      }

    "turn value into Some(X) if nullable" in withQueryResult(
      rowList1(classOf[String] -> "foo").withNullable(1, true) :+ "str") {
        implicit c =>
          SQL("SELECT 1").as(RowParser(r => Success(r.asMap)).single).
            aka("column map") must_== Map(".foo" -> Some("str"))

      }
  }

  "Column" should {
    "be extracted by name" in withQueryResult(
      rowList1(classOf[String] -> "foo") :+ "byName") { implicit c =>
        SQL("SELECT *").as(RowParser(r => Success(r[String]("foo"))).single).
          aka("column by name") must_== "byName"
      }

    "be extracted by position" in withQueryResult(stringList :+ "byPos") {
      implicit c =>
        SQL("SELECT *").as(RowParser(r => Success(r[String](1))).single).
          aka("column by name") must_== "byPos"
    }
  }

  "Row" should {
    "successfully be parsed" in withQueryResult(rowList2(
      classOf[String] -> "foo", classOf[Int] -> "num") :+ ("str", 2)) {
      implicit c =>

        SQL"SELECT *".withResult(_.map(_.row.as(
          SqlParser.str("foo") ~ SqlParser.int(2) map {
            case a ~ b => b -> a
          }))) aka "streaming result" must beRight[Option[Try[(Int, String)]]].
          which {
            _ aka "first row" must beSome[Try[(Int, String)]].which {
              _ aka "parsed value" must beSuccessfulTry(2 -> "str")
            }
          }
    }
  }
}

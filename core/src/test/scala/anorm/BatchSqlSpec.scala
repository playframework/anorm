/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

final class BatchSqlSpec extends org.specs2.mutable.Specification with H2Database {

  "Batch SQL".title

  "Creation" should {
    "fail with parameter maps not having same names" in {
      lazy val batch = BatchSql(
        "SELECT * FROM tbl WHERE a = {a}",
        Seq[NamedParameter]("a" -> 0),
        Seq[NamedParameter]("a" -> 1, "b" -> 2)
      )

      batch.aka("creation") must throwA[IllegalArgumentException](
        message = "Unexpected parameter names: a, b != expected a"
      )
    }

    "be successful with parameter maps having same names" in {
      lazy val batch = BatchSql(
        "SELECT * FROM tbl WHERE a = {a}, b = {b}",
        Seq[NamedParameter]("a" -> 0, "b" -> -1),
        Seq[NamedParameter]("a" -> 1, "b" -> 2)
      )

      lazy val expectedMaps =
        Seq(Map[String, ParameterValue]("a" -> 0, "b" -> -1), Map[String, ParameterValue]("a" -> 1, "b" -> 2))

      (batch.aka("creation") must not(
        throwA[IllegalArgumentException](message = "Unexpected parameter names: a, b != expected a")
      )).and(batch.params.aka("parameters") must_=== expectedMaps)
    }
  }

  "Appending list of parameter values" should {
    "be successful with first parameter map" in {
      val b1      = BatchSql("SELECT * FROM tbl WHERE a = {a}, b = {b}", Seq[NamedParameter]("a" -> 0, "b" -> 1), Nil)
      lazy val b2 = b1.addBatchParams(2, 3)
      lazy val expectedMaps = Seq(
        Map[String, ParameterValue]("a" -> 0, "b" -> 1),
        Map.empty[String, ParameterValue],
        Map[String, ParameterValue]("a" -> 2, "b" -> 3)
      )

      (b2.aka("append") must not(throwA[Throwable])).and(b2.params.aka("parameters") must_=== expectedMaps)
    }

    "fail with missing argument" in {
      val b1 = BatchSql("SELECT * FROM tbl WHERE a = {a}, b = {b}", Seq[NamedParameter]("a" -> 0, "b" -> 1), Nil)

      lazy val b2 = b1.addBatchParams(2)

      b2.aka("append") must throwA[IllegalArgumentException](message = "Missing parameters: b")
    }

    "be successful" in {
      val b1 = BatchSql("SELECT * FROM tbl WHERE a = {a}, b = {b}", Seq[NamedParameter]("a" -> 0, "b" -> 1), Nil)

      lazy val b2           = b1.addBatchParams(2, 3)
      lazy val expectedMaps = Seq(
        Map[String, ParameterValue]("a" -> 0, "b" -> 1),
        Map.empty[String, ParameterValue],
        Map[String, ParameterValue]("a" -> 2, "b" -> 3)
      )

      (b2.aka("append") must not(throwA[Throwable])).and(b2.params.aka("parameters") must_=== expectedMaps)
    }
  }

  "Appending list of list of parameter values" should {
    "be successful with first parameter map" in {
      val b1 = BatchSql("SELECT * FROM tbl WHERE a = {a}, b = {b}", Seq[NamedParameter]("a" -> 0, "b" -> 1), Nil)

      implicit val toParams: ToParameterList[(Int, Int)] =
        ToParameterList[(Int, Int)] {
          case (a, b) =>
            List[NamedParameter](NamedParameter.namedWithString("a" -> a), NamedParameter.namedWithString("b" -> b))
        }

      lazy val b2 = b1.bind(2 -> 3)

      lazy val expectedMaps = Seq(
        Map[String, ParameterValue]("a" -> 0, "b" -> 1),
        Map.empty[String, ParameterValue],
        Map[String, ParameterValue]("a" -> 2, "b" -> 3)
      )

      (b2.aka("append") must not(throwA[Throwable])).and(b2.params.aka("parameters") must_=== expectedMaps)
    }

    "fail with missing argument" in {
      val b1 = BatchSql(
        "SELECT * FROM tbl WHERE a = {a}, b = {b}",
        Seq[NamedParameter](Symbol("a") -> 0, Symbol("b") -> 1),
        Nil
      )

      lazy val b2 = b1.addBatchParamsList(Seq(Seq(2)))

      b2.aka("append") must throwA[IllegalArgumentException](message = "Missing parameters: b")
    }

    "be successful" in {
      val b1 = BatchSql("SELECT * FROM tbl WHERE a = {a}, b = {b}", Seq[NamedParameter]("a" -> 0, "b" -> 1), Nil)

      lazy val b2 = b1.addBatchParamsList(Seq(Seq(2, 3), Seq(4, 5)))

      lazy val expectedMaps = Seq(
        Map[String, ParameterValue]("a" -> 0, "b" -> 1),
        Map.empty[String, ParameterValue],
        Map[String, ParameterValue]("a" -> 2, "b" -> 3),
        Map[String, ParameterValue]("a" -> 4, "b" -> 5)
      )

      (b2.aka("append") must not(throwA[Throwable])).and(b2.params.aka("parameters") must_=== expectedMaps)
    }
  }

  "Batch inserting" should {
    "be successful on test1 table" in withH2Database { implicit con =>
      createTest1Table()

      lazy val batch = BatchSql(
        "INSERT INTO test1(id, foo, bar) VALUES({id}, {foo}, {bar})",
        ToParameterList.from(TestTable(1, "foo #1", 2)),
        Seq[NamedParameter]("id" -> 2, "foo" -> "foo_2", "bar" -> 4)
      )

      val stmt = TokenizedStatement(
        List(
          TokenGroup(List(StringToken("INSERT INTO test1(id, foo, bar) VALUES(")), Some("id")),
          TokenGroup(List(StringToken(", ")), Some("foo")),
          TokenGroup(List(StringToken(", ")), Some("bar")),
          TokenGroup(List(StringToken(")")), None)
        ),
        List("id", "foo", "bar")
      )

      (batch.sql.stmt.aka("parsed statement") must_=== stmt)
        .and(batch.execute().aka("batch result") must_=== Array(1, 1))
    }
  }
}

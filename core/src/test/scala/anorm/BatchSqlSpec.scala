package anorm

import acolyte.jdbc.AcolyteDSL

class BatchSqlSpec
    extends org.specs2.mutable.Specification with H2Database {

  "Batch SQL" title

  "Creation" should {
    "fail with parameter maps not having same names" in {
      lazy val batch = BatchSql(
        "SELECT * FROM tbl WHERE a = {a}",
        Seq[NamedParameter]("a" -> 0),
        Seq[NamedParameter]("a" -> 1, "b" -> 2))

      batch aka "creation" must throwA[IllegalArgumentException](
        message = "Unexpected parameter names: a, b != expected a")
    }

    "be successful with parameter maps having same names" in {
      lazy val batch = BatchSql(
        "SELECT * FROM tbl WHERE a = {a}, b = {b}",
        Seq[NamedParameter]("a" -> 0, "b" -> -1),
        Seq[NamedParameter]("a" -> 1, "b" -> 2))

      lazy val expectedMaps = Seq(
        Map[String, ParameterValue]("a" -> 0, "b" -> -1),
        Map[String, ParameterValue]("a" -> 1, "b" -> 2))

      (batch aka "creation" must not(throwA[IllegalArgumentException](
        message = "Unexpected parameter names: a, b != expected a"))).
        and(batch.params aka "parameters" must_== expectedMaps)
    }
  }

  "Appending list of parameter values" should {
    "be successful with first parameter map" in {
      val b1 = BatchSql("SELECT * FROM tbl WHERE a = {a}, b = {b}",
        Seq[NamedParameter]("a" -> 0, "b" -> 1), Nil)
      lazy val b2 = b1.addBatchParams(2, 3)
      lazy val expectedMaps = Seq(
        Map[String, ParameterValue]("a" -> 0, "b" -> 1),
        Map.empty[String, ParameterValue],
        Map[String, ParameterValue]("a" -> 2, "b" -> 3))

      (b2 aka "append" must not(throwA[Throwable])).
        and(b2.params aka "parameters" must_== expectedMaps)
    }

    "fail with missing argument" in {
      val b1 = BatchSql("SELECT * FROM tbl WHERE a = {a}, b = {b}",
        Seq[NamedParameter]("a" -> 0, "b" -> 1), Nil)

      lazy val b2 = b1.addBatchParams(2)

      b2 aka "append" must throwA[IllegalArgumentException](
        message = "Missing parameters: b")
    }

    "be successful" in {
      val b1 = BatchSql("SELECT * FROM tbl WHERE a = {a}, b = {b}",
        Seq[NamedParameter]("a" -> 0, "b" -> 1), Nil)

      lazy val b2 = b1.addBatchParams(2, 3)
      lazy val expectedMaps = Seq(
        Map[String, ParameterValue]("a" -> 0, "b" -> 1),
        Map.empty[String, ParameterValue],
        Map[String, ParameterValue]("a" -> 2, "b" -> 3))

      (b2 aka "append" must not(throwA[Throwable])).
        and(b2.params aka "parameters" must_== expectedMaps)
    }
  }

  "Appending list of list of parameter values" should {
    "be successful with first parameter map" in {
      val b1 = BatchSql("SELECT * FROM tbl WHERE a = {a}, b = {b}",
        Seq[NamedParameter]("a" -> 0, "b" -> 1), Nil)

      lazy val b2 = b1.addBatchParamsList(Seq(Seq(2, 3)))
      lazy val expectedMaps = Seq(
        Map[String, ParameterValue]("a" -> 0, "b" -> 1),
        Map.empty[String, ParameterValue],
        Map[String, ParameterValue]("a" -> 2, "b" -> 3))

      (b2 aka "append" must not(throwA[Throwable])).
        and(b2.params aka "parameters" must_== expectedMaps)
    }

    "fail with missing argument" in {
      val b1 = BatchSql("SELECT * FROM tbl WHERE a = {a}, b = {b}",
        Seq[NamedParameter]("a" -> 0, "b" -> 1), Nil)

      lazy val b2 = b1.addBatchParamsList(Seq(Seq(2)))

      b2 aka "append" must throwA[IllegalArgumentException](
        message = "Missing parameters: b")
    }

    "be successful" in {
      val b1 = BatchSql("SELECT * FROM tbl WHERE a = {a}, b = {b}",
        Seq[NamedParameter]("a" -> 0, "b" -> 1), Nil)

      lazy val b2 = b1.addBatchParamsList(Seq(Seq(2, 3), Seq(4, 5)))

      lazy val expectedMaps = Seq(
        Map[String, ParameterValue]("a" -> 0, "b" -> 1),
        Map.empty[String, ParameterValue],
        Map[String, ParameterValue]("a" -> 2, "b" -> 3),
        Map[String, ParameterValue]("a" -> 4, "b" -> 5))

      (b2 aka "append" must not(throwA[Throwable])).
        and(b2.params aka "parameters" must_== expectedMaps)
    }
  }

  "Batch inserting" should {
    "be successful on test1 table" in withH2Database { implicit con =>
      createTest1Table()

      lazy val batch = BatchSql(
        "INSERT INTO test1(id, foo, bar) VALUES({i}, {f}, {b})",
        Seq[NamedParameter]('i -> 1, 'f -> "foo #1", 'b -> 2),
        Seq[NamedParameter]('i -> 2, 'f -> "foo_2", 'b -> 4))

      val stmt = TokenizedStatement(List(TokenGroup(List(StringToken("INSERT INTO test1(id, foo, bar) VALUES(")), Some("i")), TokenGroup(List(StringToken(", ")), Some("f")), TokenGroup(List(StringToken(", ")), Some("b")), TokenGroup(List(StringToken(")")), None)), List("i", "f", "b"))

      batch.sql.stmt aka "parsed statement" mustEqual stmt and (
        batch.execute() aka "batch result" mustEqual Array(1, 1))
    }
  }
}

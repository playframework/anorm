package anorm

import java.sql.{ Array => SqlArray }

import acolyte.jdbc.QueryResult
import acolyte.jdbc.AcolyteDSL.{ connection, handleQuery, withQueryResult }
import acolyte.jdbc.RowLists.{ rowList1, rowList2, stringList }
import acolyte.jdbc.Implicits._

class SqlResultSpec extends org.specs2.mutable.Specification with H2Database {
  "SQL result" title

  "For-comprehension over result" should {
    "fail when there is no data" in withQueryResult("scalar") { implicit c =>
      lazy val parser = for {
        a <- SqlParser.str("col1")
        b <- SqlParser.int("col2")
      } yield (a -> b)

      SQL("SELECT * FROM test") as parser.single must throwA[Exception](
        message = "'col1' not found")
    }

    "return expected mandatory single result" in withQueryResult(
      rowList2(classOf[String] -> "a", classOf[Int] -> "b") :+ ("str", 2)) {
        implicit c =>
          lazy val parser = for {
            a <- SqlParser.str("a")
            b <- SqlParser.int("b")
          } yield (a -> b)

          SQL("SELECT * FROM test") as parser.single must_== ("str" -> 2)
      }

    "fail with sub-parser when there is no data" >> {
      "by throwing exception" in withQueryResult("scalar") { implicit c =>
        lazy val sub = for {
          b <- SqlParser.str("b")
          c <- SqlParser.int("c")
        } yield (b -> c)

        lazy val parser = for {
          a <- SqlParser.str("col1")
          bc <- sub
        } yield Tuple3(a, bc._1, bc._2)

        SQL("SELECT * FROM test") as parser.single must throwA[Exception](
          message = "'col1' not found")
      }

      "with captured failure" in withQueryResult("scalar") { implicit c =>
        lazy val sub = for {
          b <- SqlParser.str("b")
          c <- SqlParser.int("c")
        } yield (b -> c)

        lazy val parser = for {
          a <- SqlParser.str("col1")
          bc <- sub
        } yield Tuple3(a, bc._1, bc._2)

        SQL("SELECT * FROM test").asTry(parser.single) must beFailedTry.
          withThrowable[AnormException](".*'col1' not found.*")
      }
    }

    "fail when column is missing for sub-parser" in withQueryResult(
      rowList1(classOf[String] -> "a") :+ "str") { implicit c =>
        lazy val sub = for {
          b <- SqlParser.str("col2")
          c <- SqlParser.int("col3")
        } yield (b -> c)

        lazy val parser = for {
          a <- SqlParser.str("a")
          bc <- sub
        } yield Tuple3(a, bc._1, bc._2)

        SQL("SELECT * FROM test") as parser.single must throwA[Exception](
          message = "'col2' not found")
      }

    "return None from optional sub-parser" in withQueryResult(
      rowList1(classOf[String] -> "a") :+ "str") { implicit c =>
        lazy val sub = for {
          b <- SqlParser.str("b")
          c <- SqlParser.int("c")
        } yield (b -> c)

        lazy val parser = for {
          a <- SqlParser.str("a")
          bc <- sub.?
        } yield (a -> bc)

        SQL("SELECT * FROM test") as parser.single must_== ("str" -> None)
      }
  }

  "Column" should {
    "be found in result" in withQueryResult(
      rowList1(classOf[Float] -> "f") :+ 1.2f) { implicit c =>
        SQL("SELECT f") as SqlParser.matches("f", 1.2f).single must beTrue
      }

    "not be found in result when value not matching" in withQueryResult(
      rowList1(classOf[Float] -> "f") :+ 1.2f) { implicit c =>
        SQL("SELECT f") as SqlParser.matches("f", 2.34f).single must beFalse
      }

    "not be found in result when column missing" in withQueryResult(
      rowList1(classOf[Float] -> "f") :+ 1.2f) { implicit c =>
        SQL("SELECT f") as SqlParser.matches("x", 1.2f).single must beFalse
      }

    "be matching in result" in withQueryResult(
      rowList1(classOf[Float] -> "f") :+ 1.2f) { implicit c =>
        SQL("SELECT f") as SqlParser.matches("f", 1.2f).single must beTrue
      }

    "not be found in result when value not matching" in withQueryResult(
      rowList1(classOf[Float] -> "f") :+ 1.2f) { implicit c =>
        SQL("SELECT f") as SqlParser.matches("f", 2.34f).single must beFalse
      }

    "not be found in result when column missing" in withQueryResult(
      rowList1(classOf[Float] -> "f") :+ 1.2f) { implicit c =>
        SQL("SELECT f") as SqlParser.matches("x", 1.2f).single must beFalse
      }

    "be None when missing" in withQueryResult(
      rowList1(classOf[String] -> "foo") :+ "bar") { implicit c =>
        SQL"SELECT *".as(SqlParser.str("lorem").?.single).
          aka("result") must beNone
      }

    "be None when NULL" in withQueryResult(rowList1(
      classOf[String] -> "foo") :+ null.asInstanceOf[String]) { implicit c =>

      SQL"SELECT *".as(SqlParser.str("foo").?.single) must beNone
    }
  }

  "Collecting" should {
    sealed trait EnumX
    object Xa extends EnumX
    object Xb extends EnumX
    val pf: PartialFunction[String, EnumX] = {
      case "XA" => Xa
      case "XB" => Xb
    }

    "return Xa object" in withQueryResult(stringList :+ "XA") { implicit c =>
      SQL"SELECT str".as(SqlParser.str(1).collect("ERR")(pf).single).
        aka("collected") must_== Xa
    }

    "return Xb object" in withQueryResult(stringList :+ "XB") { implicit c =>
      SQL"SELECT str".as(SqlParser.str(1).collect("ERR")(pf).single).
        aka("collected") must_== Xb
    }

    "fail" in withQueryResult(stringList :+ "XC") { implicit c =>
      SQL"SELECT str".as(SqlParser.str(1).collect("ERR")(pf).single).
        aka("collecting") must throwA[Exception]("SqlMappingError\\(ERR\\)")
    }
  }

  "Aggregation over all rows" should {
    "release resources" in withQueryResult(
      stringList :+ "A" :+ "B" :+ "C") { implicit c =>

        val res: SqlQueryResult = SQL"SELECT str".executeQuery()
        var closed = false
        val probe = resource.managed(
          new java.io.Closeable { def close() = closed = true })

        var i = 0
        lazy val agg = res.copy(resultSet =
          res.resultSet.and(probe).map(_._1)).fold(List[Int]()) {
          (l, x) => i = i + 1; l :+ i
        }

        agg aka "aggregation" must_== Right(List(1, 2, 3)) and (
          closed aka "resource release" must beTrue) and (
            i aka "row count" must_== 3)

      }

    "release resources" in withQueryResult(
      stringList :+ "A" :+ "B" :+ "C") { implicit c =>

        val res: SqlQueryResult = SQL"SELECT str".executeQuery()
        var closed = false
        val probe = resource.managed(
          new java.io.Closeable { def close() = closed = true })

        var i = 0
        lazy val agg = res.copy(resultSet =
          res.resultSet.and(probe).map(_._1)).fold(List[Int]()) {
          (l, _) => i = i + 1; l :+ i
        }

        agg aka "aggregation" must_== Right(List(1, 2, 3)) and (
          closed aka "resource release" must beTrue) and (
            i aka "row count" must_== 3)

      }

    "release resources on exception (with degraded result set)" in {
      queryResultAndOptions((stringList :+ "A" :+ "B" :+ "C"), List(
        "acolyte.resultSet.initOnFirstRow" -> "true")) { implicit c =>

        val res: SqlQueryResult =
          SQL"SELECT str".withResultSetOnFirstRow(true).executeQuery()
        var closed = false
        val probe = resource.managed(
          new java.io.Closeable { def close() = closed = true })

        var i = 0
        lazy val agg = res.copy(resultSet = res.resultSet.and(probe).map(_._1)).
          fold(List[Int]()) { (l, _) =>
            if (i == 1) sys.error("Unexpected") else { i = i + 1; l :+ i }
          }

        agg aka "aggregation" must beLike {
          case Left(err :: Nil) =>
            err.getMessage aka "failure" must_== "Unexpected"
        } and (closed aka ("resource release") must beTrue) and (
          i aka "row count" must_== 1)

      }
    }
  }

  "Aggregation over variable number of rows" should {
    "support user alias in fold" in withQueryResult(
      stringList :+ "A" :+ "B" :+ "C") { implicit c =>
        val parser = SqlParser.str("foo")

        SQL"SELECT str".executeQuery().fold(List.empty[String],
          ColumnAliaser.withPattern(Set(1), "foo")) { (ls, row) =>
            parser(row).fold(_ => ls, _ :: ls)
          } must beRight(List("C", "B", "A"))
      }

    "support user alias in foldWhile" in withQueryResult(
      rowList1(classOf[String] -> "bar") :+ "A" :+ "B" :+ "C") { implicit c =>
        val parser = SqlParser.str("foo.bar.lorem")

        SQL"SELECT str".executeQuery().foldWhile(List.empty[String],
          ColumnAliaser.withPattern(Set(1), "foo.", ".lorem")) { (ls, row) =>
            parser(row).fold(_ => ls, _ :: ls) -> true
          } must beRight(List("C", "B", "A"))
      }

    "release resources" in withQueryResult(
      stringList :+ "A" :+ "B" :+ "C") { implicit c =>

        val res: SqlQueryResult = SQL"SELECT str".executeQuery()
        var closed = false
        val probe = resource.managed(
          new java.io.Closeable { def close() = closed = true })

        var i = 0
        lazy val agg = res.copy(resultSet =
          res.resultSet.and(probe).map(_._1)).foldWhile(List[Int]()) {
          (l, _) => i = i + 1; (l :+ i) -> true
        }

        agg aka "aggregation" must_== Right(List(1, 2, 3)) and (
          closed aka "resource release" must beTrue) and (
            i aka "row count" must_== 3)

      }

    "release resources on exception" in withQueryResult(
      stringList :+ "A" :+ "B" :+ "C") { implicit c =>

        val res: SqlQueryResult = SQL"SELECT str".executeQuery()
        var closed = false
        val probe = resource.managed(
          new java.io.Closeable { def close() = closed = true })

        var i = 0
        lazy val agg = res.copy(resultSet = res.resultSet.and(probe).map(_._1)).
          foldWhile(List[Int]()) { (l, _) =>
            if (i == 1) sys.error("Unexpected") else {
              i = i + 1; (l :+ i) -> true
            }
          }

        agg aka "aggregation" must beLike {
          case Left(err :: Nil) =>
            err.getMessage aka "failure" must_== "Unexpected"
        } and (closed aka "resource release" must beTrue) and (
          i aka "row count" must_== 1)

      }

    "stop after second row & release resources" in withQueryResult(
      stringList :+ "A" :+ "B" :+ "C") { implicit c =>

        val res: SqlQueryResult = SQL"SELECT str".executeQuery()
        var closed = false
        val probe = resource.managed(
          new java.io.Closeable { def close() = closed = true })

        var i = 0
        lazy val agg = res.copy(resultSet = res.resultSet.and(probe).map(_._1)).
          foldWhile(List[Int]()) { (l, _) =>
            if (i == 2) (l, false) else { i = i + 1; (l :+ i) -> true }
          }

        agg aka "aggregation" must_== Right(List(1, 2)) and (
          closed aka "resource release" must beTrue) and (
            i aka "row count" must_== 2)

      }
  }

  "Process variable number of rows" should {
    @annotation.tailrec
    def go(c: Option[Cursor], l: List[Row] = Nil): List[Row] = c match {
      case Some(cursor) => go(cursor.next, l :+ cursor.row)
      case _ => l
    }

    "do nothing when there is no result" in withQueryResult(QueryResult.Nil) {
      implicit c =>
        SQL"EXEC test".executeQuery().withResult(go(_)).
          aka("iteration") must beRight.which(_ aka "result list" must beEmpty)
    }

    "handle failure" in withQueryResult(
      rowList1(classOf[String] -> "foo") :+ "A" :+ "B") { implicit c =>
        var first = false
        SQL"SELECT str".executeQuery() withResult {
          case Some(_) =>
            first = true; sys.error("Failure")
          case _ => sys.error("Unexpected")
        } aka "processing with failure" must beLeft.like {
          case err :: Nil => err.getMessage aka "failure" must_== "Failure"
        } and (first aka "first read" must beTrue)
      }

    "stop after first row without failure" in withQueryResult(
      rowList1(classOf[String] -> "foo") :+ "A" :+ "B") { implicit c =>
        SQL"SELECT str".executeQuery() withResult {
          case Some(first) => Set(first.row[String]("foo"))
          case _ => Set.empty[String]
        } aka "partial processing" must_== Right(Set("A"))
      }
  }

  "SQL warning" should {
    "not be there on success" in withQueryResult(stringList :+ "A") {
      implicit c =>

        SQL"SELECT str".executeQuery().statementWarning.
          aka("statement warning") must beNone

    }

    "be handled from executed query" in withQueryResult(
      QueryResult.Nil.withWarning("Warning for test-proc-2")) { implicit c =>

        SQL("EXEC stored_proc({param})")
          .on("param" -> "test-proc-2").executeQuery()
          .statementWarning aka "statement warning" must beSome.which {
            _.getMessage aka "message" must_== "Warning for test-proc-2"
          }
      }
  }

  "Column value" should {
    val foo = s"alias-${System.identityHashCode(this)}"
    val (v1, v2) = (s"1-$foo", s"2-$foo")

    "be found by name" in withTestDB(v1) { implicit c =>
      SQL"SELECT foo AS AL, bar FROM test1".as(SqlParser.str("foo").single).
        aka("by name") must_== v1

    }

    "be found by alias" in withTestDB(v2) { implicit c =>
      SQL"SELECT foo AS AL, bar FROM test1".as(SqlParser.str("foo").single).
        aka("by name") must_== v2 and (SQL"SELECT foo AS AL, bar FROM test1".
          as(SqlParser.str("AL").single).aka("by alias") must_== v2)

    }

    "be found by alias when column name is duplicated" in {
      withH2Database { implicit c =>
        createTest1Table()

        val id1 = System.identityHashCode(c).toLong
        SQL"insert into test1(id, foo, bar) values ($id1, ${"Lorem"}, ${100})".
          execute()

        val id2 = System.identityHashCode(id1).toLong
        SQL"insert into test1(id, foo, bar) values ($id2, ${"Ipsum"}, ${101})".
          execute()

        SQL"""SELECT a.foo AS ali, b.foo, b.foo AS ias FROM test1 a
              JOIN test1 b ON a.bar <> b.bar WHERE a.id = $id1 LIMIT 1""".as(
          (SqlParser.str("ali") ~ SqlParser.str("foo") ~ SqlParser.str("ias")).
            map(SqlParser.flatten).single) must_== ("Lorem", "Ipsum", "Ipsum")
      }
    }

    "be found by user alias" in withTestDB(v2) { implicit c =>
      SQL"SELECT foo AS AL, bar FROM test1".
        asTry(SqlParser.str("pre.AL").single,
          ColumnAliaser.withPattern1("pre.")(1)).
          aka("by user alias") must beSuccessfulTry(v2) and (
            SQL"SELECT foo AS AL, bar FROM test1".asTry(
              SqlParser.str("pre.AL").single,
              ColumnAliaser.withPattern(Set(1), "pre.")).
              aka("by user alias") must beSuccessfulTry(v2))

    }
  }

  // ---

  def withTestDB[T](foo: String)(f: java.sql.Connection => T): T =
    withH2Database { implicit c =>
      createTest1Table()
      SQL("insert into test1(id, foo, bar) values ({id}, {foo}, {bar})").
        on('id -> 10L, 'foo -> foo, 'bar -> 20).execute()

      f(c)
    }

  def queryResultAndOptions[A](r: QueryResult, ps: List[(String, String)] = List.empty)(f: java.sql.Connection => A): A = f(connection(handleQuery { _ => r }, ps: _*))
}

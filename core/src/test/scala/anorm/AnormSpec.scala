package anorm

import org.specs2.mutable.Specification

import acolyte.jdbc.AcolyteDSL.{
  connection,
  handleQuery,
  handleStatement,
  updateResult,
  withQueryResult
}
import acolyte.jdbc.{
  UpdateExecution,
  QueryResult,
  ExecutedParameter
}
import acolyte.jdbc.RowLists
import RowLists.{ stringList, longList, rowList1, rowList2, rowList3 }
import acolyte.jdbc.Implicits._

import SqlParser.scalar

class AnormSpec extends Specification with H2Database with AnormTest {
  "Anorm" title

  lazy val fooBarTable = rowList3(
    classOf[Long] -> "id", classOf[String] -> "foo", classOf[Int] -> "bar")

  "Row parser" should {
    "return newly inserted data" in withH2Database { implicit c =>
      createTest1Table()
      val ex: Boolean =
        SQL"""insert into test1(id, foo, bar) 
              values (${10L}, ${"Hello"}, ${20})""".execute()

      ex aka "update executed" must beFalse /*not query*/ and {
        SQL("select * from test1 where id = {id}").on('id -> 10L)
          .as(RowParser({ row =>
            Success(row[String]("foo") -> row[Int]("bar"))
          }).single) must_== ("Hello" -> 20)
      }
    }

    "return defined option of case class" in withQueryResult(
      fooBarTable :+ (11L, "World", 21)) { implicit c =>

        SQL("SELECT * FROM test WHERE id = {id}")
          .on("id" -> 11L).as(fooBarParser1.singleOpt)
          .aka("result data") must beSome(TestTable(11L, "World", 21))

      }

    "handle scalar result" >> {
      "return single value" in withQueryResult(20) { implicit c =>
        (SQL("SELECT * FROM test").as(scalar[Int].single).
          aka("single value #1") must_== 20).
          and(SQL("SELECT * FROM test").as(scalar[Int].single) must_== 20)

      }

      "return None for missing optional value" in withQueryResult(
        null.asInstanceOf[String]) { implicit c =>
          SQL"SELECT * FROM test".withFetchSize(Some(1)).
            as(scalar[String].singleOpt) must beNone
        }

      "return 0 for missing optional numeric" in withQueryResult(
        null.asInstanceOf[Double]) { implicit c =>
          SQL("SELECT * FROM test").as(scalar[Double].singleOpt).
            aka("single value") must beSome(0d)

        }

      "throw exception when single result is missing" in withQueryResult(
        fooBarTable) { implicit c =>

          SQL("SELECT * FROM test").as(fooBarParser1.single).
            aka("mapping") must throwA[Exception].like {
              case e: Exception => e.getMessage aka "error" mustEqual (
                "SqlMappingError(No rows when expecting a single one)")
            }
        }

      "raise error when there is more than 1 required or optional row" in {
        withQueryResult(stringList :+ "A" :+ "B") { implicit c =>
          lazy val sql = SQL("SELECT 1")

          (sql.as(scalar[String].single)
            .aka("single parser") must throwA[Exception].like {
              case e: Exception => e.getMessage aka "error" mustEqual (
                "SqlMappingError(too many rows when expecting a single one)")
            }).and(sql.as(scalar[String].singleOpt)
              .aka("singleOpt parser") must throwA[Exception].like {
                case e: Exception => e.getMessage aka "error" mustEqual (
                  "SqlMappingError(too many rows when expecting a single one)")
              })
        }
      }

      "return single string from executed query" in withQueryResult(
        "Result for test-proc-1") { implicit c =>

          SQL("EXEC stored_proc({param})")
            .on("param" -> "test-proc-1").executeQuery()
            .as(scalar[String].single)
            .aka("single string") must_== "Result for test-proc-1"
        }
    }

    "handle optional property in case class" >> {
      "return instance with defined option" in withQueryResult(rowList2(
        classOf[Int] -> "id", classOf[String] -> "val") :+ (2, "str")) {
        implicit c =>

          SQL("SELECT * FROM test").as(
            SqlParser.int("id") ~ SqlParser.str("val").? map {
              case id ~ v => (id -> v)
            } single) aka "mapped data" must_== (2 -> Some("str"))

      }

      "return instance with None for column not found" in withQueryResult(
        rowList1(classOf[Long] -> "id") :+ 123l) { implicit c =>

          SQL("SELECT * FROM test").as(
            SqlParser.long("id") ~ SqlParser.str("val").? map {
              case id ~ v => (id -> v)
            } single) aka "mapped data" must_== (123l -> None)

        }

      "throw exception when type doesn't match" in withQueryResult(
        fooBarTable :+ (1l, "str", 3)) { implicit c =>

          SQL("SELECT * FROM test").as(
            SqlParser.long("id") ~ SqlParser.int("foo").? map {
              case id ~ v => (id -> v)
            } single) aka "parser" must throwA[Exception].like {
              case e: Exception => e.getMessage aka "error" must startWith(
                "TypeDoesNotMatch(Cannot convert str:")
            }
        }
    }

    "throw exception when type doesn't match" in withQueryResult("str") {
      implicit c =>
        SQL("SELECT * FROM test").as(scalar[Int].single).
          aka("mismatching type") must throwA[Exception]("TypeDoesNotMatch")

    }

    lazy val rows1 = rowList1(classOf[String] -> "val") :+ "str"
    lazy val rows2 = rowList2(
      classOf[Int] -> "id", classOf[String] -> "val") :+ (2, "str")

    "check column is found on left" >> {
      "successfully with mandatory value" in withQueryResult(rows2) {
        implicit c =>
          SQL("SELECT * FROM test").as(
            (SqlParser.int("id") ~> SqlParser.str("val")).single).
            aka("mapped data") must_== "str"

      }

      "successfully with optional value" in withQueryResult(rows1) {
        implicit c =>
          SQL("SELECT * FROM test").as(
            (SqlParser.int("id").? ~> SqlParser.str("val")).single).
            aka("mapped data") must_== "str"

      }
    }

    "check column is found on left" >> {
      "successfully with mandatory value" in withQueryResult(rows2) {
        implicit c =>
          SQL("SELECT * FROM test").as(
            (SqlParser.int("id") <~ SqlParser.str("val")).single).
            aka("mapped data") must_== 2

      }

      "successfully with optional value" in withQueryResult(rows1) {
        implicit c =>
          SQL("SELECT * FROM test").as(
            (SqlParser.str("val") <~ SqlParser.int("id").?).single).
            aka("mapped data") must_== "str"

      }
    }

    "fold row" in withQueryResult(rows2) { implicit c =>
      SQL("SELECT * FROM test").as(
        SqlParser.folder(List.empty[(Any, String, String)])({ (ls, v, m) =>
          Right((v, m.column.qualified, m.clazz) :: ls)
        }).singleOpt) must beSome(
          ("str", ".val", "java.lang.String") :: (2, ".id", "int") :: Nil)
    }
  }

  "Instance of case class" should {
    "be parsed using convience parsers with column names" in withH2Database {
      implicit c =>
        createTest1Table()
        val uc =
          SQL("insert into test1(id, foo, bar) values ({id}, {foo}, {bar})")
            .on('id -> 11L, 'foo -> "World", 'bar -> 21)
            .executeUpdate()

        uc aka "update count" must_== 1 and {
          SQL("select * from test1 where id = {id}")
            .on('id -> 11L).as(fooBarParser1.singleOpt)
            .aka("instance") must beSome(TestTable(11L, "World", 21))
        }
    }

    "be parsed using raw 'get' parser with column names" in withQueryResult(
      fooBarTable :+ (11L, "World", 21)) { implicit c =>
        SQL("insert into test1(id, foo, bar) values ({id}, {foo}, {bar})")
          .on('id -> 11L, 'foo -> "World", 'bar -> 21)
          .execute()

        SQL("select * from test1 where id = {id}")
          .on('id -> 11L).as(fooBarParser2.singleOpt)
          .aka("instance") must beSome(TestTable(11L, "World", 21))

      }

    "be parsed using convience parsers with column positions" in {
      withQueryResult(fooBarTable :+ (11L, "World", 21)) { implicit c =>
        SQL("insert into test1(id, foo, bar) values ({id}, {foo}, {bar})")
          .on('id -> 11L, 'foo -> "World", 'bar -> 21)
          .execute()

        SQL("select * from test1 where id = {id}")
          .on('id -> 11L).as(fooBarParser3.singleOpt)
          .aka("instance") must beSome(TestTable(11L, "World", 21))

      }
    }

    "be parsed using raw 'get' parser with column positions" in {
      withQueryResult(fooBarTable :+ (11L, "World", 21)) { implicit c =>
        SQL("insert into test1(id, foo, bar) values ({id}, {foo}, {bar})")
          .on('id -> 11L, 'foo -> "World", 'bar -> 21)
          .execute()

        SQL("select * from test1 where id = {id}")
          .on('id -> 11L).as(fooBarParser4.singleOpt)
          .aka("instance") must beSome(TestTable(11L, "World", 21))

      }
    }
  }

  "Result mixing named and unnamed columns" should {
    "be parsable using named and positional parsers" in withQueryResult(
      rowList3(classOf[String], classOf[String], classOf[String]).
        withLabel(2, "named") :+ ("a", "b", "c")) { implicit con =>

        SQL("SELECT *").as(mixedParser1.single).
          aka("parsed mixed result") mustEqual (("a", "b", "c"))

      }
  }

  "List" should {
    "be Nil when there is no result" in withQueryResult(QueryResult.Nil) {
      implicit c =>
        SQL("EXEC test").as(scalar[Int].*) aka "list" must_== Nil
    }

    "raise error when non-empty one is required and there is no result" in {
      withQueryResult(QueryResult.Nil) { implicit c =>
        SQL("EXEC test").as(scalar[Int].+).
          aka("non-empty list") must throwA[Throwable]("Empty Result Set")
      }
    }

    "be parsed from mapped result" in withQueryResult(
      rowList2(classOf[String] -> "foo", classOf[Int] -> "bar").
        append("row1", 100) :+ ("row2", 200)) { implicit c =>

        SQL("SELECT * FROM test").as(RowParser({ row =>
          Success(row[String]("foo") -> row[Int]("bar"))
        }).*) aka "tuple list" must_== List("row1" -> 100, "row2" -> 200)
      }

    "be parsed from class mapping" in withQueryResult(
      fooBarTable :+ (12L, "World", 101) :+ (14L, "Mondo", 3210)) {
        implicit c =>
          val exp =
            List(TestTable(12L, "World", 101), TestTable(14L, "Mondo", 3210))
          val q = SQL("SELECT * FROM test")

          (q.as(fooBarParser1.*) aka "list" must_== exp).
            and(q.as(fooBarParser1.+) aka "non-empty list" must_== exp)

      }

    "be parsed from mapping with optional column" in withQueryResult(rowList2(
      classOf[Int] -> "id", classOf[String] -> "val").
      append(9, null.asInstanceOf[String]) :+ (2, "str")) { implicit c =>

      SQL("SELECT * FROM test").as(
        SqlParser.int("id") ~ SqlParser.str("val").? map {
          case id ~ v => (id -> v)
        } *) aka "parsed list" must_== List(9 -> None, 2 -> Some("str"))
    }

    "include scalar values" in withQueryResult(
      stringList :+ "A" :+ "B" :+ "C" :+ "D") { implicit c =>

        val exp = List("A", "B", "C", "D")
        val q = SQL("SELECT c FROM letters")

        (q.as(scalar[String].*) aka "list" must_== exp).
          and(q.as(scalar[String].+) aka "non-empty list" must_== exp)
      }
  }

  "Aggregation over all rows" should {
    "be empty when there is no result" in withQueryResult(QueryResult.Nil) {
      implicit c =>
        SQL"EXEC test".fold[Option[Int]](None)({ (_, _) => Some(0) }).
          aka("aggregated value") must beRight(None)

    }

    "be parsed from mapped result" in withQueryResult(
      rowList2(classOf[String] -> "foo", classOf[Int] -> "bar").
        append("row1", 100) :+ ("row2", 200)) { implicit c =>

        SQL"SELECT * FROM test".fold(List[(String, Int)]())(
          { (l, row) => l :+ (row[String]("foo") -> row[Int]("bar")) }).
          aka("tuple stream") must_== Right(List("row1" -> 100, "row2" -> 200))

      }

    "handle failure" in withQueryResult(
      rowList1(classOf[String] -> "foo") :+ "A" :+ "B") { implicit c =>
        var i = 0
        SQL"SELECT str".fold(Set[String]()) { (l, row) =>
          if (i == 0) { i = i + 1; l + row[String]("foo") } else sys.error("Failure")

        } aka "aggregate on failure" must beLike {
          case Left(err :: Nil) => err.getMessage aka "failure" must_== "Failure"
        } and (i aka "row count" must_== 1)
      }
  }

  "Aggregation over variable number of rows" should {
    "be empty when there is no result" in withQueryResult(QueryResult.Nil) {
      implicit c =>
        SQL"EXEC test".foldWhile[Option[Int]](None)(
          { (_, _) => Some(0) -> true }).
          aka("aggregated value") must beRight(None)

    }

    "be parsed from mapped result" in withQueryResult(
      rowList2(classOf[String] -> "foo", classOf[Int] -> "bar").
        append("row1", 100) :+ ("row2", 200)) { implicit c =>

        SQL"SELECT * FROM test".foldWhile(List[(String, Int)]())({ (l, row) =>
          (l :+ (row[String]("foo") -> row[Int]("bar"))) -> true
        }) aka "tuple stream" must_== Right(List("row1" -> 100, "row2" -> 200))
      }

    "handle failure" in withQueryResult(
      rowList1(classOf[String] -> "foo") :+ "A" :+ "B") { implicit c =>
        var i = 0
        SQL"SELECT str".foldWhile(Set[String]()) { (l, row) =>
          if (i == 0) { i = i + 1; (l + row[String]("foo")) -> true }
          else sys.error("Failure")

        } aka "aggregate on failure" must beLike {
          case Left(err :: Nil) => err.getMessage aka "failure" must_== "Failure"
        } and (i aka "row count" must_== 1)
      }

    "stop after first row" in withQueryResult(
      rowList1(classOf[String] -> "foo") :+ "A" :+ "B") { implicit c =>
        var i = 0
        SQL"SELECT str".foldWhile(Set[String]()) { (l, row) =>
          if (i == 0) { i = i + 1; (l + row[String]("foo")) -> true }
          else (l, false)

        } aka "partial aggregate" must_== Right(Set("A"))
      }
  }

  "Process variable number of rows" should {
    @annotation.tailrec
    def go(c: Option[Cursor], l: List[Row] = Nil): List[Row] = c match {
      case Some(cursor) => go(cursor.next, l :+ cursor.row)
      case _ => l
    }

    @inline def withQRes[T](r: => QueryResult)(f: java.sql.Connection => T): T =
      f(connection(handleQuery(_ => r),
        "acolyte.resultSet.initOnFirstRow" -> "true"))

    "do nothing when there is no result" in withQueryResult(QueryResult.Nil) {
      implicit c =>
        SQL"EXEC test".withResult(go(_)) aka "iteration" must beRight.which {
          _ aka "result list" must beEmpty
        }
    }

    "do nothing when there is no result (with degraded result set)" in {
      withQRes(QueryResult.Nil) {
        implicit c =>
          SQL"EXEC test".withResultSetOnFirstRow(true).withResult(go(_)).
            aka("iteration") must beRight[List[Row]].like {
              case Row() :: Nil => ok
            }
      }
    }

    "handle failure" in withQueryResult(
      rowList1(classOf[String] -> "foo") :+ "A" :+ "B") { implicit c =>
        var first = false
        SQL"SELECT str" withResult {
          case Some(_) =>
            first = true; sys.error("Failure")
          case _ => sys.error("Unexpected")
        } aka "processing with failure" must beLeft.like {
          case err :: Nil => err.getMessage aka "failure" must_== "Failure"
        } and (first aka "first read" must beTrue)
      }

    "handle failure (with degraded result set)" in withQRes(
      rowList1(classOf[String] -> "foo") :+ "A" :+ "B") { implicit c =>
        var first = false
        SQL"SELECT str".withResultSetOnFirstRow(true) withResult {
          case Some(_) =>
            first = true; sys.error("Failure")
          case _ => sys.error("Unexpected")
        } aka "processing with failure" must beLeft.like {
          case err :: Nil => err.getMessage aka "failure" must_== "Failure"
        } and (first aka "first read" must beTrue)
      }

    "stop after first row without failure" in withQueryResult(
      rowList1(classOf[String] -> "foo") :+ "A" :+ "B") { implicit c =>
        SQL"SELECT str" withResult {
          case Some(first) => Set(first.row[String]("foo"))
          case _ => Set.empty[String]
        } aka "partial processing" must_== Right(Set("A"))
      }

    "stop after first row without failure (with degraded result set)" in {
      withQRes(rowList1(classOf[String] -> "foo") :+ "A" :+ "B") { implicit c =>
        SQL"SELECT str".withResultSetOnFirstRow(true) withResult {
          case Some(first) => Set(first.row[String]("foo"))
          case _ => Set.empty[String]
        } aka "partial processing" must_== Right(Set("A"))
      }
    }
  }

  "Insertion" should {
    def con = connection(handleStatement withUpdateHandler {
      case UpdateExecution("INSERT ?", ExecutedParameter(1) :: Nil) => 1
      case UpdateExecution("INSERT ?", ExecutedParameter(2) :: Nil) =>
        updateResult(2, longList :+ 3L)
      case UpdateExecution("INSERT ?", ExecutedParameter(3) :: Nil) =>
        updateResult(3, stringList :+ "generated")

    })

    "return no generated key" in {
      implicit val c = con
      SQL"INSERT ${1}".executeInsert() aka "insertion" must beNone
    }

    "return numeric key (default)" in {
      implicit val c = con
      SQL"INSERT ${2}".executeInsert() aka "insertion" must beSome(3L)
    }

    "fail to return unsupported generated key" in {
      implicit val c = con
      SQL"INSERT ${3}".executeInsert().
        aka("insertion") must throwA[Exception]("TypeDoesNotMatch")
    }

    "return generated keys" >> {
      "as string" in {
        implicit val c = con
        SQL"INSERT ${3}".executeInsert(scalar[String].singleOpt).
          aka("insertion") must beSome("generated")
      }

      "as long with column selection" in withH2Database { implicit c =>
        val tableName = s"foo${System identityHashCode c}"

        createTable(tableName, "id bigint auto_increment", "name varchar")

        @inline def insert(n: String, ns: String*) =
          SQL"insert into #${tableName}(name) values(${c.toString})".
            executeInsert1(n, ns: _*)()

        insert("id") must beSuccessfulTry(Some(1L)) and (
          insert("id") must beSuccessfulTry(Some(2L))) and (
            insert("value") aka "ignore invalid key" must beSuccessfulTry(
              Some(3L)))

      }
    }
  }

  "Query" should {
    "be executed as simple SQL" in withQueryResult(
      RowLists.booleanList :+ true) { implicit con =>
        val sql = SQL("SELECT 1")

        implicitly[Sql](sql).
          aka("converted") must beAnInstanceOf[SimpleSql[_]] and (
            SQL("SELECT 1").execute() aka "executed" must beTrue)
      }
  }

  "Timestamp wrapper" should {
    "not match with invalid instance" in {
      ("Foo" match {
        case TimestampWrapper1(ts) => true
        case _ => false
      }) aka "matching" must beFalse
    }

    "fail when error raised from .getTimestamp" in {
      ((new {
        def getTimestamp: java.sql.Timestamp = sys.error("Foo")
      }) match {
        case TimestampWrapper1(ts) => true
        case _ => false
      }) aka "matching" must throwA[Exception]("Foo")
    }

    "successfully match" in {
      val ts = new java.sql.Timestamp(System.identityHashCode(this).toLong)

      ((new {
        val getTimestamp = ts
      }) match {
        case TimestampWrapper1(v) => Some(v)
        case _ => Option.empty[Long]
      }) aka "timestamp" must beSome(ts)
    }
  }
}

sealed trait AnormTest { db: H2Database =>
  import SqlParser.{ get, int, long, str }

  val fooBarParser1 = long("id") ~ str("foo") ~ int("bar") map {
    case id ~ foo ~ bar => TestTable(id, foo, bar)
  }

  val fooBarParser2 =
    get[Long]("id") ~ get[String]("foo") ~ get[Int]("bar") map {
      case id ~ foo ~ bar => TestTable(id, foo, bar)
    }

  val fooBarParser3 = long(1) ~ str(2) ~ int(3) map {
    case id ~ foo ~ bar => TestTable(id, foo, bar)
  }

  val fooBarParser4 = get[Long](1) ~ get[String](2) ~ get[Int](3) map {
    case id ~ foo ~ bar => TestTable(id, foo, bar)
  }

  val mixedParser1 = str(1) ~ str("named") ~ str(3) map {
    case i ~ j ~ k => (i, j, k)
  }
}

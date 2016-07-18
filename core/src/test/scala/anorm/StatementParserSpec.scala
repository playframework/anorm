package anorm

import acolyte.jdbc.{
  QueryExecution,
  DefinedParameter => DParam,
  ParameterMetaData => ParamMeta
}
import acolyte.jdbc.RowLists.stringList
import acolyte.jdbc.AcolyteDSL.{ connection, handleQuery, withQueryResult }
import acolyte.jdbc.Implicits._

class StatementParserSpec extends org.specs2.mutable.Specification {
  "SQL statement parser" title

  "Statement" should {
    "be parsed with 'name' and 'cat' parameters and support multiple lines" in {
      SqlStatementParser.parse("""
        SELECT * FROM schema.table
        -- Foo's comment
        WHERE (name = {name} AND category = {cat}) OR id = ?
      """) aka "updated statement and parameters" must beSuccessfulTry(
        TokenizedStatement(List(TokenGroup(List(StringToken("""SELECT * FROM schema.table
        -- Foo's comment
        WHERE (name = """)), Some("name")), TokenGroup(List(StringToken(" AND category = ")), Some("cat")), TokenGroup(List(StringToken(""") OR id = ?
      """)), None)), List("name", "cat")))

    }

    "detect missing query parameter" in withQueryResult(stringList :+ "test") {
      implicit con =>
        SQL("SELECT name FROM t WHERE id = {id}").execute().
          aka("query") must throwA[java.util.NoSuchElementException]
    }

    "reserved '%' character must be preserved as special token" in {
      SqlStatementParser.parse(
        "SELECT * FROM Test WHERE id = {id} AND n LIKE '%strange%s fruit%s'").
        aka("statement") must beSuccessfulTry(TokenizedStatement(List(TokenGroup(List(StringToken("SELECT * FROM Test WHERE id = ")), Some("id")), TokenGroup(List(StringToken(" AND n LIKE '"), PercentToken, StringToken("strange"), PercentToken, StringToken("s fruit"), PercentToken, StringToken("s'")), None)), List("id")))

    }
  }

  "Value" should {
    def frag[A](v: A)(implicit c: ToSql[A] = null): (String, Int) =
      Option(c).fold("?" -> 1)(_.fragment(v))

    "give single-value '?' SQL fragment" in {
      frag("str").aka("SQL fragment") mustEqual ("?" -> 1)
    }

    "give multi-value '?, ?, ?' SQL fragment" in {
      frag(Seq("A", "B", "C")) aka "SQL fragment" mustEqual ("?, ?, ?" -> 3)
    }

    "give multi-value 'x=? OR x=? OR x=?' SQL fragment" in {
      frag(SeqParameter(Seq("A", "B", "C"), " OR ", "x=")).
        aka("SQL fragment") mustEqual ("x=? OR x=? OR x=?" -> 3)
    }
  }

  "Tokenized statement" should {
    "return some prepared query with updated statement" in {
      val stmt1 = TokenizedStatement(List(TokenGroup(List(StringToken("SELECT * FROM t WHERE c IN (")), Some("cs")), TokenGroup(List(StringToken(") AND id = ")), Some("id"))), List("cs", "id"))

      val stmt2 = TokenizedStatement(List(TokenGroup(List(StringToken("SELECT * FROM t WHERE c IN ("), StringToken("?, ?"), StringToken(") AND id = ")), Some("id"))), List("cs_1", "cs_2", "id"))

      val stmt3 = TokenizedStatement(List(TokenGroup(List(StringToken("SELECT * FROM t WHERE c IN ("), StringToken("?, ?"), StringToken(") AND id = "), StringToken("?")), None)), List("cs", "id"))

      // ---

      Sql.prepareQuery(stmt1.tokens, stmt1.names, Map[String, ParameterValue]("cs" -> List("a", "b"), "id" -> 3), 0, new StringBuilder(), List.empty[(Int, ParameterValue)]) must beSuccessfulTry.like {
        case prepared1 =>
          prepared1._2 aka "parameters #1" must_== List[(Int, ParameterValue)](
            0 -> List("a", "b"), 2 -> 3) and {
              Sql.prepareQuery(stmt2.tokens, stmt2.names, Map[String, ParameterValue]("cs_1" -> "a", "cs_2" -> "b", "id" -> 3), 0, new StringBuilder(), List.empty[(Int, ParameterValue)]) must beSuccessfulTry.like {
                case prepared2 =>
                  prepared1._1 aka "sql" must_== prepared2._1 and (
                    prepared2._2 aka "parameters #2" must_== (
                      List[(Int, ParameterValue)](0 -> "a", 1 -> "b", 2 -> 3)))
              }
            } and {
              Sql.prepareQuery(stmt3.tokens, stmt3.names, Map[String, ParameterValue]("cs" -> List("a", "b"), "id" -> 3), 0, new StringBuilder(), List.empty[(Int, ParameterValue)]) must beSuccessfulTry.like {
                case prepared3 =>
                  prepared3._1 aka "sql" must_== prepared1._1 and (
                    prepared3._2 aka "parameters #3" must_== prepared1._2)
              }
            }
      }
    }

    val stmt = TokenizedStatement(List(TokenGroup(List(StringToken("SELECT * FROM name LIKE "), StringToken("'"), PercentToken, StringToken("strange"), StringToken("'"), StringToken(" AND id = ")), Some("id"))), List("id"))

    "not be prepared as SQL if there is missing parameter" in {
      Sql.prepareQuery(stmt.tokens, stmt.names, Map.empty[String, ParameterValue], 0, new StringBuilder(), List.empty[(Int, ParameterValue)]) must beFailedTry.
        like {
          case err: Sql.MissingParameter =>
            err.getMessage must startWith(
              """Missing parameter value after: "SELECT""")
        }
    }

    "properly written as prepared SQL" in {
      Sql.prepareQuery(stmt.tokens, stmt.names, Map[String, ParameterValue]("id" -> "foo"), 0, new StringBuilder(), List.empty[(Int, ParameterValue)]) must beSuccessfulTry.like {
        case (sql, (0, pv) :: Nil) =>
          sql must_== "SELECT * FROM name LIKE '%strange' AND id = ?" and (
            pv must_== ParameterValue.toParameterValue("foo"))
      }
    }
  }

  "String interpolation" should {
    "be successfully prepared" in {
      val hell = "sinki"
      val query = SQL"""
         SELECT * FROM (SELECT 'Hello' AS COL1, 'World' AS COL2) AS MY_TABLE WHERE COL1 LIKE $hell + '%'
      """

      query.sql.stmt aka "tokenized" must_== TokenizedStatement(List(
        TokenGroup(List(StringToken("""
         SELECT * FROM (SELECT 'Hello' AS COL1, 'World' AS COL2) AS MY_TABLE WHERE COL1 LIKE """)), Some("_0")),
        TokenGroup(List(StringToken(""" + '%'
      """)), None)), List("_0")) and {
        query.sql.paramsInitialOrder aka "parameter names" must_== List("_0")
      } and {
        query.params aka "parameters" must_== Map[String, ParameterValue](
          "_0" -> "sinki")
      }
    }

    "handle values as '#' escaped part in statement or SQL parameters" in {
      val cmd = "SELECT"
      val clause = "FROM"
      val table = "Test"
      implicit val con = connection(handleQuery {
        case QueryExecution(
          "SELECT * FROM Test WHERE id = ? AND code IN (?, ?)",
          DParam("id1", ParamMeta.Str) :: DParam(2, ParamMeta.Int) ::
            DParam(5, ParamMeta.Int) :: Nil) => stringList :+ "ok"

        case QueryExecution(s, p) => stringList :+ "ko"
      })

      lazy val query = SQL"""#$cmd * #$clause #$table WHERE id = ${"id1"} AND code IN (${Seq(2, 5)})"""

      query.sql.stmt aka "tokenized statement" must_== TokenizedStatement(List(TokenGroup(List(StringToken("SELECT"), StringToken(" * "), StringToken("FROM"), StringToken(" "), StringToken("Test"), StringToken(" WHERE id = ")), Some("_0")), TokenGroup(List(StringToken(" AND code IN (")), Some("_1")), TokenGroup(List(StringToken(")")), None)), List("_0", "_1")) and (query.as(SqlParser.scalar[String].single) must_== "ok")
    }
  }
}

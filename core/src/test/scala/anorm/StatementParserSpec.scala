package anorm

import acolyte.jdbc.{
  QueryExecution,
  DefinedParameter => DParam,
  ParameterMetaData => ParamMeta
}
import acolyte.jdbc.RowLists.stringList
import acolyte.jdbc.AcolyteDSL.{ connection, handleQuery, withQueryResult }
import acolyte.jdbc.Implicits._

object StatementParserSpec extends org.specs2.mutable.Specification {
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

  /* TODO: Refactor
  "Rewriting" should {
    "return some prepared query with updated statement" in {
      val stmt1 = TokenizedStatement(List(TokenGroup(List(StringToken("SELECT * FROM t WHERE c IN (")), Some("cs")), TokenGroup(List(StringToken(") AND id = ")), Some("id"))), List("cs", "id"))

      val stmt2 = TokenizedStatement(List(TokenGroup(List(StringToken("SELECT * FROM t WHERE c IN ("), StringToken("?, ?"), StringToken(") AND id = ")), Some("id"))), List("cs", "id"))

      val stmt3 = TokenizedStatement(List(TokenGroup(List(StringToken("SELECT * FROM t WHERE c IN ("), StringToken("?, ?"), StringToken(") AND id = "), StringToken("?")), None)), List("cs", "id"))

      TokenizedStatement.rewrite(stmt1, "?, ?") must beSuccessfulTry.like {
        case rw1 =>
          rw1 aka "first rewrite" mustEqual stmt2 and (
            TokenizedStatement.rewrite(rw1, "?").
            aka("second rewrite") must beSuccessfulTry.like {
              case rw2 => rw2 aka "final statement" must_== stmt3
            })
      }
    }

    "return no prepared query" in {
      TokenizedStatement.rewrite(TokenizedStatement(List(TokenGroup(
        List(StringToken("SELECT * FROM Test WHERE id = ?")), None)), Nil),
        "x") aka "rewrite" must beFailedTry
    }
  }

  "Tokenized statement" should {
    val stmt = TokenizedStatement(List(TokenGroup(List(StringToken("SELECT * FROM name LIKE "), StringToken("'"), PercentToken, StringToken("strange"), StringToken("'"), StringToken(" AND id = ")), Some("id"))), List("id"))

    "not be prepared as SQL if there is some placeholder not rewritten" in {
      TokenizedStatement.toSql(stmt) must beFailedTry
    }

    "properly written as prepared SQL" in {
      TokenizedStatement.rewrite(stmt, "?") must beSuccessfulTry.like {
        case rw =>
          TokenizedStatement.toSql(rw) must beSuccessfulTry("SELECT * FROM name LIKE '%strange' AND id = ?")
      }
    }
  }
   */

  "String interpolation" should {
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

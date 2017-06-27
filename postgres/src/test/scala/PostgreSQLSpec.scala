import java.util.UUID

import java.sql.Connection

import play.api.libs.json.{ Json, JsObject, JsNumber, JsValue, Reads, Writes }

import anorm._, postgresql._

import org.postgresql.util.PGobject

import acolyte.jdbc.RowLists
import acolyte.jdbc.AcolyteDSL, AcolyteDSL.{ handleStatement, withQueryResult }

import acolyte.jdbc.{
  ParameterMetaData,
  UpdateExecution,
  DefinedParameter,
  ExecutedParameter => P
}

class PostgreSQLSpec extends org.specs2.mutable.Specification {
  "PostgreSQL support" title

  import acolyte.jdbc.Implicits._

  "JsValue" should {
    "be passed as JSONB" >> {
      val JsDef = ParameterMetaData.Default(JsObjectParameterMetaData.jdbcType)
      val ExpectedStmt = "INSERT INTO test(id, json) VALUES (?, ?)"

      def withPgo[T](id: String, json: String)(f: Connection => T): T = {
        val JsVal = {
          val pgo = new PGobject()
          pgo.setType(JsObjectParameterMetaData.sqlType)
          pgo.setValue(json)
          pgo
        }

        def con = AcolyteDSL.connection(handleStatement withUpdateHandler {
          case UpdateExecution(ExpectedStmt,
            P(`id`) :: DefinedParameter(JsVal, JsDef) :: Nil) => {

            1 // update count
          }
        })

        f(con)
      }

      "when is an object" in {
        withPgo("foo", """{"bar":1}""") { implicit con =>
          SQL"""INSERT INTO test(id, json) VALUES (${"foo"}, ${Json.obj("bar" -> 1)})""".executeUpdate() must_== 1
        }
      }

      "when is a string" in {
        withPgo("foo", "\"bar\"") { implicit con =>
          SQL"""INSERT INTO test(id, json) VALUES (${"foo"}, ${Json toJson "bar"})""".executeUpdate() must_== 1
        }
      }

      "when is a number" in {
        withPgo("foo", "3") { implicit con =>
          SQL"""INSERT INTO test(id, json) VALUES (${"foo"}, ${Json toJson 3L})""".executeUpdate() must_== 1
        }
      }

      "using JSON writer" in {
        withPgo("foo", "2") { implicit con =>
          SQL"""INSERT INTO test(id, json) VALUES (${"foo"}, ${asJson(Lorem)})""".executeUpdate() must_== 1
        }
      }
    }

    "be selected from JSONB" >> {
      val table = RowLists.rowList1(classOf[PGobject] -> "json")
      val jsonb = {
        val pgo = new PGobject()
        pgo.setType(JsObjectParameterMetaData.sqlType)
        pgo.setValue("""{"bar":1}""")
        pgo
      }
      val jsVal = Json.obj("bar" -> 1)

      "successfully" in withQueryResult(table :+ jsonb) { implicit con =>
        SQL"SELECT json FROM test".
          as(SqlParser.scalar[JsValue].single) must_== jsVal
      }

      "successfully as JsObject" in withQueryResult(table :+ jsonb) {
        implicit con =>
          SQL"SELECT json FROM test".
            as(SqlParser.scalar[JsObject].single) must_== jsVal
      }

      "successfully using a Reads" in withQueryResult(table :+ jsonb) {
        implicit con =>
          SQL"SELECT json FROM test".
            as(SqlParser.scalar(fromJson[TestEnum]).single) must_== Bar
      }
    }
  }

  "UUID" should {
    "be passed as PostgreSQL UUID" in {
      implicit val con = AcolyteDSL.connection(
        handleStatement withUpdateHandler {
          case UpdateExecution("INSERT INTO test_seq VALUES(?::UUID)",
            DefinedParameter(uuid: String, ParameterMetaData.Str) :: Nil) => {

            try {
              UUID.fromString(uuid)
              1
            } catch {
              case _: Throwable => -1
            }
          }

          case _ => 0
        })

      SQL"INSERT INTO test_seq VALUES(${UUID.randomUUID()})".
        executeUpdate() must_== 1
    }
  }

  // ---

  sealed trait TestEnum
  case object Bar extends TestEnum
  case object Lorem extends TestEnum

  object TestEnum {
    implicit val r: Reads[TestEnum] = Reads[TestEnum] { js =>
      (js \ "bar").validate[Int].map {
        case 1 => Bar
        case _ => Lorem
      }
    }

    implicit val w: Writes[TestEnum] = Writes[TestEnum] {
      case Lorem => JsNumber(2)
      case _ => JsNumber(1)
    }
  }
}

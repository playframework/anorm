import anorm._, postgresql._
import play.api.libs.json.{ Json, JsObject, JsValue, Reads }

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

      "when is an object" in {
        val JsVal = {
          val pgo = new PGobject()
          pgo.setType(JsObjectParameterMetaData.sqlType)
          pgo.setValue("""{"bar":1}""")
          pgo
        }

        // JsObjectParameterMetaData.
        implicit val con = AcolyteDSL.connection(
          handleStatement withUpdateHandler {
            case UpdateExecution(ExpectedStmt,
              P("foo") :: DefinedParameter(JsVal, JsDef) :: Nil) => {

              1 // update count
            }
          })

        SQL"""INSERT INTO test(id, json) VALUES (${"foo"}, ${Json.obj("bar" -> 1)})""".executeUpdate() must_== 1
      }

      "when is a string" in {
        val JsVal = {
          val pgo = new PGobject()
          pgo.setType(JsObjectParameterMetaData.sqlType)
          pgo.setValue(""""bar"""")
          pgo
        }

        // JsObjectParameterMetaData.
        implicit val con = AcolyteDSL.connection(
          handleStatement withUpdateHandler {
            case UpdateExecution(ExpectedStmt,
              P("foo") :: DefinedParameter(JsVal, JsDef) :: Nil) => {

              1 // update count
            }
          })

        SQL"""INSERT INTO test(id, json) VALUES (${"foo"}, ${Json toJson "bar"})""".executeUpdate() must_== 1
      }

      "when is a number" in {
        val JsVal = {
          val pgo = new PGobject()
          pgo.setType(JsObjectParameterMetaData.sqlType)
          pgo.setValue("""3""")
          pgo
        }

        // JsObjectParameterMetaData.
        implicit val con = AcolyteDSL.connection(
          handleStatement withUpdateHandler {
            case UpdateExecution(ExpectedStmt,
              P("foo") :: DefinedParameter(JsVal, JsDef) :: Nil) => {

              1 // update count
            }
          })

        SQL"""INSERT INTO test(id, json) VALUES (${"foo"}, ${Json toJson 3L})""".executeUpdate() must_== 1
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
            as(SqlParser.scalar[Family].single) must_== Bar
      }
    }
  }

  // ---

  sealed trait Family
  case object Bar extends Family
  case object Lorem extends Family

  object Family {
    implicit val r: Reads[Family] = Reads[Family] { js =>
      (js \ "bar").validate[Int].map {
        case 1 => Bar
        case _ => Lorem
      }
    }
  }
}

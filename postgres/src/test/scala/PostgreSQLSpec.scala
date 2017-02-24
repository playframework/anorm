import anorm._, postgresql._
import play.api.libs.json.Json

import acolyte.jdbc.AcolyteDSL, AcolyteDSL.handleStatement

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
    "passed as JSONB" >> {
      val JsDef = ParameterMetaData.Default(JsObjectParameterMetaData.jdbcType)
      val ExpectedStmt = "INSERT INTO test(id, json) VALUES (?, ?)"

      "when is an object" in {
        val JsVal = {
          val pgo = new org.postgresql.util.PGobject()
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
          val pgo = new org.postgresql.util.PGobject()
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
          val pgo = new org.postgresql.util.PGobject()
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
  }
}

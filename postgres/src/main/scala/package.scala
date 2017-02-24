package anorm

import play.api.libs.json.{ JsObject, Json, JsValue }

import org.postgresql.util.PGobject

package object postgresql {
  /** Allows pass a `JsValue` as parameter to be stored as `PGobject`. */
  implicit def jsValueToStatement[J <: JsValue] = ToStatement[J] { (s, i, js) =>
    val pgObject = new PGobject()
    pgObject.setType(JsValueParameterMetaData.sqlType)
    pgObject.setValue(Json.stringify(js))
    s.setObject(i, pgObject, JsValueParameterMetaData.jdbcType)
  }

  implicit object JsValueParameterMetaData extends ParameterMetaData[JsValue] {
    val sqlType = "JSONB"
    val jdbcType = java.sql.Types.OTHER
  }

  implicit object JsObjectParameterMetaData
      extends ParameterMetaData[JsObject] {
    val sqlType = "JSONB"
    val jdbcType = java.sql.Types.OTHER
  }
}


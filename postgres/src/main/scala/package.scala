package anorm

import play.api.libs.json.{ JsError, JsObject, Json, JsSuccess, JsValue, Reads }

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

  implicit val jsValueColumn: Column[JsValue] =
    Column.nonNull[JsValue] { (value, meta) =>
      @inline def str: Option[String] = value match {
        case o: PGobject => Some(o.getValue)
        case s: String => Some(s)
        case clob: java.sql.Clob => Some(
          clob.getSubString(1, clob.length.toInt))
      }

      str match {
        case Some(js) => try {
          Right(Json parse js)
        } catch {
          case cause: Throwable => Left(SqlRequestError(cause))
        }

        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value:${value.asInstanceOf[AnyRef].getClass} to JsValue for column ${meta.column}")
        )
      }
    }

  implicit val jsObjectColumn: Column[JsObject] = jsValueColumn.flatMap {
    case obj @ JsObject(_) => Right(obj)
    case js => Left(TypeDoesNotMatch(
      s"JsValue found, but JsObject expected: ${Json stringify js}"))
  }

  implicit def jsonReadsColumn[T](implicit r: Reads[T]): Column[T] =
    jsValueColumn.flatMap {
      r.reads(_) match {
        case JsSuccess(v, _) => Right(v)
        case err @ JsError(_) => Left(TypeDoesNotMatch(
          s"JSON validation error: ${Json.stringify(JsError toJson err)}"))
      }
    }
}


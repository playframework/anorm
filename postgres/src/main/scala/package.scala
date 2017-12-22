package anorm

import java.util.UUID

import scala.util.control.NonFatal

import play.api.libs.json.{
  JsError,
  JsObject,
  Json,
  JsSuccess,
  JsValue,
  Reads,
  Writes
}

import org.postgresql.util.PGobject

package object postgresql extends PGJson {

  /** Instance of `ToSql` to support PostgreSQL UUID parameter */
  implicit val UUIDToSql: ToSql[UUID] = ToSql[UUID] { _ => "?::UUID" -> 1 }
}

// Could be moved to a separate module
sealed trait PGJson {
  /** Allows to pass a `JsValue` as parameter to be stored as `PGobject`. */
  implicit def jsValueToStatement[J <: JsValue] = ToStatement[J] { (s, i, js) =>
    val pgObject = new PGobject()
    pgObject.setType(JsValueParameterMetaData.sqlType)
    pgObject.setValue(Json.stringify(js))
    s.setObject(i, pgObject, JsValueParameterMetaData.jdbcType)
  }

  implicit object JsObjectParameterMetaData
    extends ParameterMetaData[JsObject] {
    val sqlType = "JSONB"
    val jdbcType = java.sql.Types.OTHER
  }

  /**
   * @tparam the type of value to be written as JSON
   * @param value the value to be passed as a JSON parameter
   * @param w the Play writes to be used to serialized the value as JSON
   *
   * {{{
   * import play.api.libs.json._
   * import anorm._, postgresql._
   *
   * case class Foo(bar: String)
   * implicit val w: Writes[Foo] = Json.writes[Foo]
   *
   * val value = Foo("lorem")
   * SQL"INSERT INTO test(id, json) VALUES(\${"bar"}, \${value})".executeUpdate()
   * }}}
   */
  def asJson[T](value: T)(implicit w: Writes[T]): ParameterValue =
    anorm.postgresql.PGUtil.asJson[T](value)(w)

  implicit object JsValueParameterMetaData extends ParameterMetaData[JsValue] {
    val sqlType = "JSONB"
    val jdbcType = java.sql.Types.OTHER
  }

  /**
   * {{{
   * import play.api.libs.json.JsValue
   * import anorm.{ SQL, SqlParser }
   * import anorm.postgresql._
   *
   * SQL"SELECT json FROM test".as(SqlParser.scalar[JsValue].single)
   * }}}
   */
  implicit val jsValueColumn: Column[JsValue] =
    Column.nonNull[JsValue] { (value, meta) =>
      @inline def str: Option[String] = value match {
        case o: PGobject => Some(o.getValue)
        case s: String => Some(s)
        case clob: java.sql.Clob => Some(
          clob.getSubString(1, clob.length.toInt))
        case _ => None
      }

      str match {
        case Some(js) => try {
          Right(Json parse js)
        } catch {
          case NonFatal(cause) => Left(SqlRequestError(cause))
        }

        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value:${value.getClass} to JsValue for column ${meta.column}"))
      }
    }

  /**
   * {{{
   * import play.api.libs.json.JsObject
   * import anorm.{ SQL, SqlParser }
   * import anorm.postgresql._
   *
   * SQL"SELECT json FROM test".as(SqlParser.scalar[JsObject].single)
   * }}}
   */
  implicit val jsObjectColumn: Column[JsObject] = jsValueColumn.mapResult {
    case obj @ JsObject(_) => Right(obj)
    case js => Left(TypeDoesNotMatch(
      s"JsValue found, but JsObject expected: ${Json stringify js}"))
  }

  /**
   * @param r the Play reader to be used from the selected JSONB value
   *
   * {{{
   * import play.api.libs.json.Reads
   * import anorm.{ SQL, SqlParser }
   * import anorm.postgresql._
   *
   * def foo(implicit r: Reads[Foo]): Foo
   *   SQL"SELECT json FROM test".
   *     as(SqlParser.scalar(fromJson[Foo]).single)
   * }}}
   */
  def fromJson[T](implicit r: Reads[T]): Column[T] = jsValueColumn.mapResult {
    r.reads(_) match {
      case JsSuccess(v, _) => Right(v)
      case err @ JsError(_) => Left(TypeDoesNotMatch(
        s"JSON validation error: ${Json.stringify(JsError toJson err)}"))
    }
  }
}

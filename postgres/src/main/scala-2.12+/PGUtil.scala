package anorm.postgresql

import play.api.libs.json.{ JsValue, Writes }

import anorm.{ ParameterValue, ToStatement }

private[anorm] object PGUtil {
  def asJson[T](value: T)(implicit w: Writes[T]): ParameterValue = {
    implicit val writeJsonToStatement: ToStatement[T] =
      jsValueToStatement[JsValue].contramap(w.writes(_))

    ParameterValue.from(value)
  }
}

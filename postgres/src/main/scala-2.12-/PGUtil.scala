package anorm.postgresql

import play.api.libs.json.{ JsValue, Writes }

import anorm.{ ParameterValue, ToStatement }

private[anorm] object PGUtil {
  def asJson[T](value: T)(implicit w: Writes[T]): ParameterValue = {
    implicit val writeJsonToStatement = ToStatement[T] { (s, i, b) =>
      jsValueToStatement[JsValue].set(s, i, w.writes(b))
    }

    ParameterValue.from(value)
  }
}

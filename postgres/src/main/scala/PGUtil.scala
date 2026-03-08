/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

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

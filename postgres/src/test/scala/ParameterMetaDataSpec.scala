/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

import play.api.libs.json.{ JsObject, JsValue }

import anorm.ParameterMetaData

final class ParameterMetaDataSpec extends org.specs2.mutable.Specification {
  "Parameter metadata".title

  import anorm.postgresql._

  "Metadata" should {
    "be provided for parameter" >> {
      "of type JsValue" in {
        Option(implicitly[ParameterMetaData[JsValue]].sqlType).aka("SQL type") must beSome("JSONB")
      }
      "of type JsObject" in {
        Option(implicitly[ParameterMetaData[JsObject]].sqlType).aka("SQL type") must beSome("JSONB")
      }
    }
  }
}

import anorm.ParameterMetaData

import play.api.libs.json.{ JsObject, JsValue }

class ParameterMetaDataSpec extends org.specs2.mutable.Specification {
  "Parameter metadata" title

  import anorm.postgresql._

  "Metadata" should {
    "be provided for parameter" >> {
      "of type JsValue" in {
        Option(implicitly[ParameterMetaData[JsValue]].sqlType).
          aka("SQL type") must beSome("JSONB")
      }
      "of type JsObject" in {
        Option(implicitly[ParameterMetaData[JsObject]].sqlType).
          aka("SQL type") must beSome("JSONB")
      }
    }
  }
}

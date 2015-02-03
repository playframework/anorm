package anorm

import java.time.{ Instant, LocalDateTime, ZonedDateTime }

object Java8ParameterMetaDataSpec extends org.specs2.mutable.Specification {
  "Java8 Parameter metadata" title

  "Metadata" should {
    // ... not be found without explicit import
    shapeless.test.illTyped("implicitly[ParameterMetaData[Instant]]")
    shapeless.test.illTyped("implicitly[ParameterMetaData[LocalDateTime]]")
    shapeless.test.illTyped("implicitly[ParameterMetaData[ZonedDateTime]]")

    import Java8._

    "be provided for parameter" >> {
      s"of type Instant" in {
        Option(implicitly[ParameterMetaData[Instant]].sqlType).
          aka("SQL type") must beSome
      }

      s"of type LocalDateTime" in {
        Option(implicitly[ParameterMetaData[LocalDateTime]].sqlType).
          aka("SQL type") must beSome
      }

      s"of type ZonedDateTime" in {
        Option(implicitly[ParameterMetaData[ZonedDateTime]].sqlType).
          aka("SQL type") must beSome
      }
    }
  }
}

package anorm

class SqlRequestErrorSpec extends org.specs2.mutable.Specification {
  "SQL request error" title

  "ColumnNotFound" should {
    "be converted to Failure" in {
      ColumnNotFound("foo", List("bar")).toFailure must beFailedTry.
        withThrowable[AnormException]("'foo' not found, available columns: bar")
    }
  }

  "UnexpectedNullableFound" should {
    "be converted to Failure" in {
      UnexpectedNullableFound("Foo message").toFailure must beFailedTry.
        withThrowable[AnormException](
          "UnexpectedNullableFound\\(Foo message\\)")
    }
  }

  "SqlMappingError" should {
    "be converted to Failure" in {
      SqlMappingError("Foo").toFailure must beFailedTry.
        withThrowable[AnormException]("SqlMappingError\\(Foo\\)")
    }
  }

  "TypeDoesNotMatch" should {
    "be converted to Failure" in {
      TypeDoesNotMatch("Foo").toFailure must beFailedTry.
        withThrowable[AnormException]("TypeDoesNotMatch\\(Foo\\)")
    }
  }
}

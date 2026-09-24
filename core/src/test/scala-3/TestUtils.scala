/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import scala.compiletime.testing.typeCheckErrors

import org.specs2.matcher.{ Expectable, MatchResult, Matcher }

object TestUtils:

  final case class TypecheckResult(error: Option[String])

  inline def typecheck(inline code: String): TypecheckResult =
    TypecheckResult(typeCheckErrors(code).headOption.map(_.message))

  def failWith(expected: String): Matcher[TypecheckResult] =
    new Matcher[TypecheckResult]:
      def apply[S <: TypecheckResult](actual: Expectable[S]): MatchResult[S] =
        val message = actual.value.error.getOrElse("the code typechecks ok")
        result(
          actual.value.error.exists(_.replaceAll("[\\n\\r]", "").matches(s"(?s).*$expected.*")),
          "found expected typecheck error",
          s"$message does not match $expected",
          actual
        )

end TestUtils

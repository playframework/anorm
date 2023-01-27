/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import org.specs2.execute.{ Typecheck, Typechecked }

object TestUtils {
  import scala.language.experimental.macros

  def typecheck(code: String): Typechecked = macro Typecheck.typecheckImpl
}

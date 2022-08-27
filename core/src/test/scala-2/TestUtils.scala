package anorm

import org.specs2.execute.{ Typecheck, Typechecked }

object TestUtils {
  import scala.language.experimental.macros

  def typecheck(code: String): Typechecked = macro Typecheck.typecheckImpl
}

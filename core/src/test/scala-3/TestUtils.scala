package anorm

import scala.compiletime.testing.{ typeCheckErrors, Error }

import org.specs2.execute.{ TypecheckError, TypecheckSuccess, Typechecked }

object TestUtils:

  inline def typecheck(inline code: String): Typechecked =
    typeCheckErrors(code).headOption match {
      case Some(Error(msg, _, _, _)) =>
        Typechecked(code, TypecheckError(msg))

      case _ =>
        Typechecked(code, TypecheckSuccess)
    }

end TestUtils

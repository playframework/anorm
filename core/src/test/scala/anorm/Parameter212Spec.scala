/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import acolyte.jdbc.{ DefinedParameter => DParam, ParameterMetaData => ParamMeta, UpdateExecution }
import acolyte.jdbc.AcolyteDSL.{ connection, handleStatement }
import acolyte.jdbc.Implicits._

final class Parameter212Spec extends org.specs2.mutable.Specification {
  "Parameter (2.12+)".title

  val SqlStr = ParamMeta.Str

  "ToStatement" should {
    implicit def con: java.sql.Connection =
      connection(handleStatement.withUpdateHandler {
        case UpdateExecution("EXEC proc ?", DParam("value:2", SqlStr) :: Nil) =>
          1

        case _ =>
          0
      })

    "be contramap'ed" in {
      implicit val to: ToStatement[Int] = ToStatement.of[String].contramap[Int] { i => s"value:$i" }

      SQL"EXEC proc ${2}".executeUpdate() must_=== 1
    }
  }
}

/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.enumeratum.values

import acolyte.jdbc.{ DefinedParameter => DParam, ParameterMetaData => ParamMeta, UpdateExecution }
import acolyte.jdbc.AcolyteDSL.{ connection, handleStatement }
import acolyte.jdbc.Implicits._

import anorm._

final class ValueEnumToStatementSpec extends org.specs2.mutable.Specification {
  "ValueEnum parameters".title

  "Sensitive ValueEnum" should {
    "successfully passed as parameter" >> {
      def spec(value: Drink, repr: Short) =
        value.toString in withConnection(repr) { implicit c =>
          SQL"set-short ${value}" match {
            case q @ SimpleSql(_, _, _, _) =>
              // execute = false: update ok but returns no resultset
              // see java.sql.PreparedStatement#execute
              q.execute() mustEqual false
          }
        }

      spec(Drink.OrangeJuice, 1)
      spec(Drink.AppleJuice, 2)
      spec(Drink.Cola, 3)
      spec(Drink.Beer, 4)
    }
  }

  // ---

  private val SqlShort = ParamMeta.Short

  private def withConnection[A](repr: Short)(f: java.sql.Connection => A): A =
    f(connection(handleStatement.withUpdateHandler {
      case UpdateExecution("set-short ?", DParam(`repr`, SqlShort) :: Nil) => 1 /* case ok */

      case _ =>
        throw new Exception("Unexpected execution")

    }))
}

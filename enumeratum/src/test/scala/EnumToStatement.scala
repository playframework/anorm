/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.enumeratum

import acolyte.jdbc.{ DefinedParameter => DParam, ParameterMetaData => ParamMeta, UpdateExecution }
import acolyte.jdbc.AcolyteDSL.{ connection, handleStatement }
import acolyte.jdbc.Implicits._

import anorm._

final class EnumToStatementSpec extends org.specs2.mutable.Specification {
  "Enum parameters".title

  "Sensitive enum" should {
    "be successfully passed as parameter" >> {
      def spec(value: Dummy, repr: String) =
        repr in withConnection(repr) { implicit c =>
          SQL"set-str ${value}" match {
            case q @ SimpleSql(_, _, _, _) =>
              // execute = false: update ok but returns no resultset
              // see java.sql.PreparedStatement#execute
              q.execute() mustEqual false
          }
        }

      spec(Dummy.A, "A")
      spec(Dummy.B, "B")
      spec(Dummy.c, "c")
    }
  }

  "Insensitive enum" should {
    "be successfully passed as parameter" >> {
      def spec(value: InsensitiveDummy, repr: String) =
        repr in withConnection(repr) { implicit c =>
          SQL"set-str ${value}" match {
            case q @ SimpleSql(_, _, _, _) =>
              // execute = false: update ok but returns no resultset
              // see java.sql.PreparedStatement#execute
              q.execute() mustEqual false
          }
        }

      spec(InsensitiveDummy.A, "A")
      spec(InsensitiveDummy.B, "B")
      spec(InsensitiveDummy.c, "c")
    }
  }

  "Lowercase enum" should {
    "be successfully passed as parameter" >> {
      def spec(value: LowercaseDummy, repr: String) =
        repr in withConnection(repr) { implicit c =>
          SQL"set-str ${value}" match {
            case q @ SimpleSql(_, _, _, _) =>
              // execute = false: update ok but returns no resultset
              // see java.sql.PreparedStatement#execute
              q.execute() mustEqual false
          }
        }

      spec(LowercaseDummy.Apple, "apple")
      spec(LowercaseDummy.Banana, "banana")
      spec(LowercaseDummy.Cherry, "cherry")
    }
  }

  "Uppercase enum" should {
    "be successfully passed as parameter" >> {
      def spec(value: UppercaseDummy, repr: String) =
        repr in withConnection(repr) { implicit c =>
          SQL"set-str ${value}" match {
            case q @ SimpleSql(_, _, _, _) =>
              // execute = false: update ok but returns no resultset
              // see java.sql.PreparedStatement#execute
              q.execute() mustEqual false
          }
        }

      spec(UppercaseDummy.Apple, "APPLE")
      spec(UppercaseDummy.Banana, "BANANA")
      spec(UppercaseDummy.Cherry, "CHERRY")
    }
  }

  // ---

  private val SqlStr = ParamMeta.Str

  private def withConnection[A](repr: String)(f: java.sql.Connection => A): A =
    f(connection(handleStatement.withUpdateHandler {
      case UpdateExecution("set-str ?", DParam(`repr`, SqlStr) :: Nil) => 1 /* case ok */

      case _ =>
        throw new Exception("Unexpected execution")

    }))
}

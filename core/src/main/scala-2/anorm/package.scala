/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package object anorm extends anorm.TopLevelDefinitions {

  /**
   * Creates an SQL query using String Interpolation feature.
   * It is a 1-step alternative for SQL("...").on(...) functions.
   *
   * {{{
   * import java.util.Date
   * import java.sql.Connection
   *
   * import anorm._
   *
   * case class Computer(
   *   name: String,
   *   introduced: Date,
   *   discontinued: Date,
   *   companyId: String)
   *
   * def foo(computer: Computer, id: String)(implicit con: Connection) =
   *   SQL"""
   *     UPDATE computer SET name = \\${computer.name},
   *     introduced = \\${computer.introduced},
   *     discontinued = \\${computer.discontinued},
   *     company_id = \\${computer.companyId}
   *     WHERE id = \\$id
   *   """.executeUpdate()
   * }}}
   */
  implicit class SqlStringInterpolation(val sc: StringContext) extends AnyVal {
    def SQL(args: ParameterValue*) = {
      val (ts, ps) = TokenizedStatement.stringInterpolation(sc.parts, args)
      SimpleSql(SqlQuery.prepare(ts, ts.names), ps, RowParser(Success(_)))
    }
  }
}

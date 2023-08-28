/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

private[anorm] object TopLevel extends TopLevelDefinitions

export TopLevel.{
  TimestampWrapper1,
  TimestampWrapper2,
  StringWrapper2,
  SQL,
  StatementResource,
  statementClassTag,
  resultSetClassTag,
  ResultSetResource,
  features
}

implicit class SqlStringInterpolation(val sc: StringContext) extends AnyVal {
  def SQL(args: ParameterValue*) = {
    val (ts, ps) = TokenizedStatement.stringInterpolation(sc.parts, args)
    SimpleSql(SqlQuery.prepare(ts, ts.names), ps, RowParser(Success(_)))
  }
}

/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import scala.language.implicitConversions

private[anorm] trait PackageCompat {
  // TODO: Review implicit usage there
  // (add explicit functions on SqlQuery?)
  implicit def sqlToSimple(sql: SqlQuery): SimpleSql[Row] = sql.asSimple
}

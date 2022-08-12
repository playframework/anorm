package anorm

import scala.language.implicitConversions

private[anorm] trait PackageCompat {
  // TODO: Review implicit usage there
  // (add explicit functions on SqlQuery?)
  implicit def sqlToSimple(sql: SqlQuery): SimpleSql[Row] = sql.asSimple
}

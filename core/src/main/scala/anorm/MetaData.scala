package anorm

import scala.collection.breakOut

/**
 * @param qualified the qualified column name
 * @param alias the column alias
 */
case class ColumnName(qualified: String, alias: Option[String])

/**
 * @param column the name of the column
 * @param nullable true if the column is nullable
 * @param clazz the class of the JDBC column value
 */
case class MetaDataItem(column: ColumnName, nullable: Boolean, clazz: String)

private[anorm] case class MetaData(ms: List[MetaDataItem]) {
  /** Returns meta data for specified column. */
  def get(columnName: String): Option[MetaDataItem] = {
    val key = columnName.toUpperCase
    aliasedDictionary.get(key).
      orElse(dictionary2 get key).orElse(dictionary get key)
  }

  private lazy val dictionary: Map[String, MetaDataItem] =
    ms.map(m => m.column.qualified.toUpperCase() -> m).toMap

  private lazy val dictionary2: Map[String, MetaDataItem] = ms.map { m =>
    val column = m.column.qualified.split('.').last;
    column.toUpperCase() -> m
  }(breakOut)

  private lazy val aliasedDictionary: Map[String, MetaDataItem] =
    ms.flatMap { m =>
      m.column.alias.map(a => Map(a.toUpperCase() -> m)).getOrElse(Map.empty)
    }(breakOut)

  lazy val columnCount = ms.size

  lazy val availableColumns: List[String] =
    ms.flatMap(i => i.column.qualified :: i.column.alias.toList)

}

/** Allows to define or overwrite the alias for a column. */
trait ColumnAliaser extends Function[(Int, ColumnName), Option[String]] {
  /**
   * Returns the alias for the specified column, if defined.
   *
   * @param column the position (>= 1) and the name of the column
   */
  def apply(column: (Int, ColumnName)): Option[String]
}

object ColumnAliaser {
  import scala.collection.immutable.Set

  private class Default(
    f: PartialFunction[(Int, ColumnName), String]) extends ColumnAliaser {

    def apply(column: (Int, ColumnName)) = f.lift(column)
  }

  /**
   * Initializes an aliaser from a given partial function.
   *
   * {{{
   * ColumnAliaser({
   *   case (1, cn) => "my_id"
   *   case (_, ColumnName(".foo", _)) => "prefix.foo"
   * })
   * }}}
   */
  def apply(f: PartialFunction[(Int, ColumnName), String]): ColumnAliaser =
    new Default(f)

  object empty extends ColumnAliaser {
    def apply(column: (Int, ColumnName)) = Option.empty[String]
  }

  /**
   * @param positions the column positions (>= 1)
   * @param as function to determine the alias for the matching columns
   *
   * {{{
   * ColumnAliaser.perPositions((2 to 3).toSet) {
   *   case (2, _) => "prefix.foo"
   *   case _ => "bar"
   * }
   * }}}
   */
  def perPositions(positions: Set[Int])(as: ((Int, ColumnName)) => String): ColumnAliaser = new Default({
    case c @ (pos, _) if (positions contains pos) => as(c)
  })

  /**
   * @param positions the column positions (>= 1)
   * @param prefix the prefix to be prepended to the aliases of the matching columns
   * @param suffix the suffix to be appended to the aliases (default: `""`)
   *
   * {{{
   * ColumnAliaser.withPattern((2 to 3).toSet, "prefix.")
   * }}}
   */
  def withPattern(positions: Set[Int], prefix: String, suffix: String = ""): ColumnAliaser = perPositions(positions) {
    case (_, ColumnName(_, Some(alias))) => s"$prefix$alias$suffix"
    case (_, ColumnName(_, _)) => s"$prefix$suffix"
  }

  /**
   * @param prefix the prefix to be prepended to the aliases of the matching columns
   * @param suffix the suffix to be appended to the aliases (default: `""`)
   * @param positions the column positions (>= 1); duplicate are merged
   *
   * {{{
   * ColumnAliaser.withPattern((2 to 3).toSet, "prefix.")
   * }}}
   */
  def withPattern1(prefix: String, suffix: String = "")(positions: Int*): ColumnAliaser = withPattern(positions.toSet, prefix, suffix)
}

private[anorm] object MetaData {
  import java.sql.{ ResultSet, ResultSetMetaData }
  import scala.language.reflectiveCalls

  private type PgMeta = { def getBaseTableName(i: Int): String }

  /** Returns metadata for given result set. */
  def parse(rs: ResultSet, as: ColumnAliaser): MetaData = {
    val meta = rs.getMetaData()
    val nbColumns = meta.getColumnCount()
    MetaData(List.range(1, nbColumns + 1).map { i =>
      val cn = ColumnName(
        {
          if (meta.getClass.getName startsWith "org.postgresql.") {
            // HACK FOR POSTGRES:
            // Fix in https://github.com/pgjdbc/pgjdbc/pull/107

            meta.asInstanceOf[PgMeta].getBaseTableName(i)
          } else {
            meta.getTableName(i)
          }

        } + "." + meta.getColumnName(i),
        alias = Option(meta.getColumnLabel(i)))

      val colName = as(i -> cn).fold(cn)(a => cn.copy(alias = Some(a)))

      MetaDataItem(
        column = colName,
        nullable = meta.isNullable(i) == ResultSetMetaData.columnNullable,
        clazz = meta.getColumnClassName(i))
    })
  }
}

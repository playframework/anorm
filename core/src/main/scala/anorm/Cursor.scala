/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import java.sql.ResultSet

/** Result cursor */
sealed trait Cursor {

  /** Current row */
  def row: Row

  /** Cursor to next row */
  def next: Option[Cursor]

  override lazy val toString = s"Cursor($row)"
}

/** Cursor companion */
object Cursor {

  /**
   * Returns cursor for next row in given result set.
   *
   * @param rs Result set, must be before first row
   * @return None if there is no result in the set
   */
  private[anorm] def apply(rs: ResultSet, aliaser: ColumnAliaser): Option[Cursor] =
    if (!rs.next) None else Some(withMeta(rs, MetaData.parse(rs, aliaser)))

  def unapply(cursor: Cursor): Option[(Row, Option[Cursor])] =
    Some(cursor.row -> cursor.next)

  /**
   * Returns a cursor for a result set initialized on the first row.
   *
   * @param rs Result set, initialized on the first row
   */
  private[anorm] def onFirstRow(rs: ResultSet, aliaser: ColumnAliaser): Option[Cursor] = try {
    Some(new Cursor {
      val meta                        = MetaData.parse(rs, aliaser)
      val columns: List[Int]          = List.range(1, meta.columnCount + 1)
      val row: anorm.Cursor.ResultRow = ResultRow(meta, columns.map(rs.getObject(_)))

      def next = apply(rs, meta, columns)
    })
  } catch {
    case scala.util.control.NonFatal(_) => Option.empty[Cursor]
  }

  /** Returns a cursor with already parsed metadata. */
  private def withMeta(rs: ResultSet, _meta: MetaData): Cursor = new Cursor {
    val meta                        = _meta
    val columns: List[Int]          = List.range(1, meta.columnCount + 1)
    val row: anorm.Cursor.ResultRow = ResultRow(meta, columns.map(rs.getObject(_)))

    lazy val next = apply(rs, meta, columns)
  }

  /** Creates cursor after the first one, as meta data is already known. */
  private def apply(rs: ResultSet, meta: MetaData, columns: List[Int]): Option[Cursor] = if (!rs.next) None
  else
    Some(new Cursor {
      val row: anorm.Cursor.ResultRow = ResultRow(meta, columns.map(rs.getObject(_)))
      def next                        = if (!rs.next) None else Some(withMeta(rs, meta))
    })

  /** Result row to be parsed. */
  private case class ResultRow(metaData: MetaData, data: List[Any]) extends Row {

    override lazy val toString = "Row(" + Compat
      .lazyZip(metaData.ms, data)
      .map((m, v) => s"'${m.column}': ${v} as ${m.clazz}")
      .mkString(", ") + ")"
  }
}

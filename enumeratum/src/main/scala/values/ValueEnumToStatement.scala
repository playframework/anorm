package anorm.enumeratum.values

import java.sql.PreparedStatement

import _root_.enumeratum.values.ValueEnumEntry
import anorm.ToStatement

private[values] object ValueEnumToStatement {
  def apply[ValueType, EntryType <: ValueEnumEntry[ValueType]](implicit
      baseToStmt: ToStatement[ValueType]
  ): ToStatement[EntryType] = new ToStatement[EntryType] {
    def set(s: PreparedStatement, i: Int, e: EntryType) =
      baseToStmt.set(s, i, e.value)
  }
}

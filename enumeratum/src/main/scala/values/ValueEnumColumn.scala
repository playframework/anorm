package anorm.enumeratum.values

import _root_.enumeratum.values.{ ValueEnum, ValueEnumEntry }
import anorm.{ Column, TypeDoesNotMatch }

private[values] object ValueEnumColumn {
  def apply[ValueType, EntryType <: ValueEnumEntry[ValueType]](
    e: ValueEnum[ValueType, EntryType])(
    implicit
    baseColumn: Column[ValueType]): Column[EntryType] = Column.nonNull[EntryType] {
    case (value, meta) =>
      baseColumn(value, meta) match {
        case Left(err) =>
          Left(err)

        case Right(s) =>
          e.withValueOpt(s) match {
            case Some(obj) => Right(obj)
            case None => Left(TypeDoesNotMatch(s"Invalid value: $s"))
          }
      }
  }
}

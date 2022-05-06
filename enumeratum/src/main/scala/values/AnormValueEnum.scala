package anorm.enumeratum.values

import _root_.enumeratum.values.{
  ByteEnum,
  ByteEnumEntry,
  CharEnum,
  CharEnumEntry,
  IntEnum,
  IntEnumEntry,
  LongEnum,
  LongEnumEntry,
  ShortEnum,
  ShortEnumEntry,
  StringEnum,
  StringEnumEntry,
  ValueEnum,
  ValueEnumEntry
}
import anorm.{ Column, ToStatement }

trait AnormValueEnum[ValueType, EntryType <: ValueEnumEntry[ValueType]] {
  enum: ValueEnum[ValueType, EntryType] =>

  /**
   * Column instance for the entries of this enum
   */
  implicit def column: Column[EntryType]

  /**
   * ToStatement instance for the entries of this enum
   */
  implicit def toStatement: ToStatement[EntryType]
}

/**
 * Enum implementation for Int enum members
 * that provides instances for Anorm typeclasses
 */
trait IntAnormValueEnum[EntryType <: IntEnumEntry] extends AnormValueEnum[Int, EntryType] {
  this: IntEnum[EntryType] =>

  implicit val column: Column[EntryType] = ValueEnumColumn(this)

  implicit val toStatement: ToStatement[EntryType] =
    ValueEnumToStatement[Int, EntryType]
}

/**
 * Enum implementation for Long enum members
 * that provides instances for Anorm typeclasses.
 */
trait LongAnormValueEnum[EntryType <: LongEnumEntry] extends AnormValueEnum[Long, EntryType] {
  this: LongEnum[EntryType] =>

  implicit val column: Column[EntryType] = ValueEnumColumn(this)

  implicit val toStatement: ToStatement[EntryType] =
    ValueEnumToStatement[Long, EntryType]
}

/**
 * Enum implementation for Short enum members
 * that provides instances for Anorm typeclasses
 */
trait ShortAnormValueEnum[EntryType <: ShortEnumEntry] extends AnormValueEnum[Short, EntryType] {
  this: ShortEnum[EntryType] =>

  implicit val column: Column[EntryType] = ValueEnumColumn(this)

  implicit val toStatement: ToStatement[EntryType] =
    ValueEnumToStatement[Short, EntryType]
}

/**
 * Enum implementation for String enum members
 * that provides instances for Anorm typeclasses
 */
trait StringAnormValueEnum[EntryType <: StringEnumEntry] extends AnormValueEnum[String, EntryType] {
  this: StringEnum[EntryType] =>
  implicit val column: Column[EntryType] = ValueEnumColumn(this)

  implicit val toStatement: ToStatement[EntryType] =
    ValueEnumToStatement[String, EntryType]
}

/**
 * Enum implementation for Char enum members
 * that provides instances for Anorm typeclasses
 */
trait CharAnormValueEnum[EntryType <: CharEnumEntry] extends AnormValueEnum[Char, EntryType] {
  this: CharEnum[EntryType] =>
  implicit val column: Column[EntryType] = ValueEnumColumn(this)

  implicit val toStatement: ToStatement[EntryType] =
    ValueEnumToStatement[Char, EntryType]
}

/**
 * Enum implementation for Byte enum members
 * that provides instances for Anorm typeclasses
 */
trait ByteAnormValueEnum[EntryType <: ByteEnumEntry] extends AnormValueEnum[Byte, EntryType] {
  this: ByteEnum[EntryType] =>
  implicit val column: Column[EntryType] = ValueEnumColumn(this)

  implicit val toStatement: ToStatement[EntryType] =
    ValueEnumToStatement[Byte, EntryType]
}

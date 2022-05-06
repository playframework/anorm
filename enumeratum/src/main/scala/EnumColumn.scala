package anorm.enumeratum

import _root_.enumeratum.{ Enum, EnumEntry }
import anorm.{ Column, TypeDoesNotMatch }

private[enumeratum] object EnumColumn {
  def column[A <: EnumEntry](e: Enum[A], insensitive: Boolean): Column[A] =
    if (insensitive) {
      parse[A](e.withNameInsensitiveOption)
    } else {
      parse[A](e.withNameOption)
    }

  def lowercaseOnlyColumn[A <: EnumEntry](e: Enum[A]): Column[A] =
    parse[A](e.withNameLowercaseOnlyOption)

  def uppercaseOnlyColumn[A <: EnumEntry](e: Enum[A]): Column[A] =
    parse[A](e.withNameUppercaseOnlyOption)

  // ---

  private def parse[A <: EnumEntry](extract: String => Option[A]): Column[A] =
    Column.nonNull[A] {
      case (s: String, _) =>
        extract(s) match {
          case Some(result) => Right(result)
          case None         => Left(TypeDoesNotMatch(s"Invalid value: $s"))
        }

      case (_, meta) =>
        Left(TypeDoesNotMatch(s"Column '${meta.column.qualified}' expected to be String; Found ${meta.clazz}"))
    }

}

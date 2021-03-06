package anorm.enumeratum

import _root_.enumeratum.{ Enum, EnumEntry }
import anorm.{ Column, TypeDoesNotMatch }

private[enumeratum] object EnumColumn {
  def column[A <: EnumEntry](enum: Enum[A], insensitive: Boolean): Column[A] =
    if (insensitive) {
      parse[A](enum.withNameInsensitiveOption)
    } else {
      parse[A](enum.withNameOption)
    }

  def lowercaseOnlyColumn[A <: EnumEntry](enum: Enum[A]): Column[A] =
    parse[A](enum.withNameLowercaseOnlyOption)

  def uppercaseOnlyColumn[A <: EnumEntry](enum: Enum[A]): Column[A] =
    parse[A](enum.withNameUppercaseOnlyOption)

  // ---

  private def parse[A <: EnumEntry](extract: String => Option[A]): Column[A] =
    Column.nonNull[A] {
      case (s: String, _) =>
        extract(s) match {
          case Some(result) => Right(result)
          case None => Left(TypeDoesNotMatch(s"Invalid value: $s"))
        }

      case (_, meta) =>
        Left(
          TypeDoesNotMatch(
            s"Column '${meta.column.qualified}' expected to be String; Found ${meta.clazz}"))
    }

}

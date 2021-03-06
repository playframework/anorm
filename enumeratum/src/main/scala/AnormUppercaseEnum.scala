package anorm.enumeratum

import java.sql.PreparedStatement

import _root_.enumeratum.{ Enum, EnumEntry }
import anorm.{ Column, ToStatement }

/**
 * Provides uppercase instances for Anorm typeclasses:
 *
 * - [[anorm.Column]]
 * - [[anorm.ToStatement]]
 */
trait AnormUppercaseEnum[A <: EnumEntry] { self: Enum[A] =>
  implicit val column: Column[A] =
    EnumColumn.uppercaseOnlyColumn[A](self)

  implicit val toStatement = new ToStatement[A] {
    def set(s: PreparedStatement, i: Int, v: A) =
      s.setString(i, v.entryName.toUpperCase)
  }
}

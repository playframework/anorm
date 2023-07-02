/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.enumeratum

import java.sql.PreparedStatement

import _root_.enumeratum.{ Enum, EnumEntry }
import anorm.{ Column, ToStatement }

/**
 * Provides insensitive instances for Anorm typeclasses:
 *
 * - [[anorm.Column]]
 * - [[anorm.ToStatement]]
 */
trait AnormInsensitiveEnum[A <: EnumEntry] { self: Enum[A] =>
  implicit val column: Column[A] =
    EnumColumn.column[A](self, insensitive = true)

  implicit val toStatement: ToStatement[A] = new ToStatement[A] {
    def set(s: PreparedStatement, i: Int, v: A) =
      s.setString(i, v.entryName)
  }
}

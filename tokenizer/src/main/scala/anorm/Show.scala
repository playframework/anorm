/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

/** Typeclass for string representation */
trait Show {
  def show: String
}

object Show {
  trait Maker[T] extends (T => Show) {

    /** Show maker for the appropriate subject type. */
    def apply(subject: T): Show
  }

  object Maker {
    object Identity extends Maker[Show] {
      def apply(s: Show): Show = s
    }
  }

  /** Returns the string representation for the given subject. */
  def mkString[T](subject: T)(implicit maker: Maker[T]): String =
    maker(subject).show
}

final class StringShow(underlying: String) extends Show {
  def show = underlying
}

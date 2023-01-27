/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

/** Set value as prepared SQL statement fragment. */
@annotation.implicitNotFound("No SQL renderer found for parameter of type ${A}: `anorm.ToSql[${A}]` required")
trait ToSql[A] {

  /**
   * Prepares SQL fragment for value,
   * using `java.sql.PreparedStatement` syntax (with '?').
   *
   * @return SQL fragment and count of "?" placeholders in it
   */
  def fragment(value: A): (String, Int)
}

/** Provided ToSql implementations. */
object ToSql {
  import scala.collection.immutable.SortedSet

  private class FunctionalToSql[A](f: A => (String, Int)) extends ToSql[A] {
    def fragment(value: A): (String, Int) = f(value)
  }

  /** Functional factory */
  def apply[A](f: A => (String, Int)): ToSql[A] = new FunctionalToSql(f)

  /**
   * Returns fragment for each value, separated by ", ".
   *
   * {{{
   * anorm.ToSql.listToSql[Int].fragment(List(1, 3, 5))
   * // "?, ?, ?"
   * }}}
   */
  implicit def listToSql[A](implicit conv: ToSql[A] = ToSql.missing[A]): ToSql[List[A]] = traversableToSql[A, List[A]]

  /**
   * Returns fragment for each value, separated by ", ".
   *
   * {{{
   * anorm.ToSql.seqToSql[String].fragment(Seq("A", "B", "C"))
   * // "?, ?, ?"
   * }}}
   */
  implicit def seqToSql[A](implicit conv: ToSql[A] = ToSql.missing[A]): ToSql[Seq[A]] = traversableToSql[A, Seq[A]]

  /**
   * Returns fragment for each value, separated by ", ".
   *
   * {{{
   * anorm.ToSql.setToSql[Int].fragment(Set(1, 3, 5))
   * // "?, ?, ?"
   * }}}
   */
  implicit def setToSql[A](implicit conv: ToSql[A] = ToSql.missing[A]): ToSql[Set[A]] = traversableToSql[A, Set[A]]

  /**
   * Returns fragment for each value, separated by ", ".
   *
   * {{{
   * import scala.collection.immutable.SortedSet
   *
   * anorm.ToSql.sortedSetToSql[String].fragment(SortedSet("A", "B", "C"))
   * // "?, ?, ?"
   * }}}
   */
  implicit def sortedSetToSql[A](implicit conv: ToSql[A] = ToSql.missing[A]): ToSql[SortedSet[A]] =
    traversableToSql[A, SortedSet[A]]

  /**
   * Returns fragment for each value, separated by ", ".
   */
  implicit def streamToSql[A](implicit conv: ToSql[A] = ToSql.missing[A]): ToSql[Compat.LazyLst[A]] =
    traversableToSql[A, Compat.LazyLst[A]]

  /**
   * Returns fragment for each value, separated by ", ".
   *
   * {{{
   * anorm.ToSql.vectorToSql[String].fragment(Vector("A", "B", "C"))
   * // "?, ?, ?"
   * }}}
   */
  implicit def vectorToSql[A](implicit conv: ToSql[A] = ToSql.missing[A]): ToSql[Vector[A]] =
    traversableToSql[A, Vector[A]]

  /** Returns fragment for each value, with custom formatting. */
  implicit def seqParamToSql[A](implicit conv: ToSql[A] = ToSql.missing[A]): ToSql[SeqParameter[A]] =
    ToSql[SeqParameter[A]] { p =>
      val before = p.before.getOrElse("")
      val after  = p.after.getOrElse("")
      val c: A => (String, Int) =
        if (conv == null) _ => "?" -> 1 else conv.fragment

      val sql = p.values.foldLeft(new StringBuilder() -> 0) {
        case ((sb, i), v) =>
          val frag = c(v)
          val st =
            if (i > 0) sb ++= p.separator ++= before ++= frag._1
            else sb ++= before ++= frag._1

          (st ++= after, i + frag._2)
      }

      sql._1.toString -> sql._2
    }

  @inline private def traversableToSql[A, T <: Compat.Trav[A]](implicit conv: ToSql[A]) = ToSql[T] { values =>
    val c: A => (String, Int) =
      if (conv == null) _ => "?" -> 1 else conv.fragment

    val sql = values.foldLeft(new StringBuilder() -> 0) {
      case ((sb, i), v) =>
        val frag = c(v)
        val st   = if (i > 0) sb ++= ", " ++= frag._1 else sb ++= frag._1

        (st, i + frag._2)
    }

    sql._1.toString -> sql._2
  }

  /** Fallback when no instance is available. */
  @deprecated("Do not use", "2.5.4")
  def missing[A] = null
}

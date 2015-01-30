package anorm

import java.sql.PreparedStatement

/** Prepared parameter value. */
sealed trait ParameterValue extends Show {

  /**
   * Writes placeholder(s) in [[java.sql.PreparedStatement]] syntax
   * (with '?') for this parameter in initial statement (with % placeholder).
   *
   * @param stmt SQL statement (with %s placeholders)
   * @param offset Position offset for this parameter
   * @return Update statement with '?' placeholder(s) for parameter,
   * with offset for next parameter
   */
  def toSql(stmt: TokenizedStatement, offset: Int): (TokenizedStatement, Int)
  // @todo Type with validation

  /**
   * Sets this value on given statement at specified index.
   *
   * @param s SQL Statement
   * @param index Parameter index
   */
  def set(s: PreparedStatement, index: Int): Unit

  /** Returns string representation of this value. */
  def show: String

  @deprecated(message = "Use [[show]]", since = "2.3.8")
  final def stringValue = show
}

/**
 * Value factory for parameter.
 *
 * {{{
 * val param = ParameterValue("str", null, setter)
 *
 * SQL("...").onParams(param)
 * }}}
 */
object ParameterValue {
  import scala.language.implicitConversions

  private[anorm] trait Wrapper[T] { def value: T }

  @throws[IllegalArgumentException]("if value `v` is null whereas `toStmt` is marked with [[anorm.NotNullGuard]]") // TODO: MayErr on conversion to parameter values?
  def apply[A](v: A, s: ToSql[A], toStmt: ToStatement[A]) = (v, toStmt) match {
    case (null, _: NotNullGuard) => throw new IllegalArgumentException()
    case _ => new ParameterValue with Wrapper[A] {
      val value = v

      def toSql(stmt: TokenizedStatement, o: Int): (TokenizedStatement, Int) = {
        val frag: (String, Int) =
          if (s == null) ("?" -> 1) else s.fragment(value)

        TokenizedStatement.rewrite(stmt, frag._1).toOption.
          fold[(TokenizedStatement, Int)](
            /* ignore extra parameter */ stmt -> o)((_, o + frag._2))
      }

      def set(s: PreparedStatement, i: Int) = toStmt.set(s, i, value)

      lazy val show = s"$value"
      override lazy val toString = s"ParameterValue($value)"
      override lazy val hashCode = value.hashCode

      override def equals(that: Any) = that match {
        case o: Wrapper[A] => (o.value == value)
        case _ => false
      }
    }
  }

  implicit def toParameterValue[A](a: A)(implicit s: ToSql[A] = null, p: ToStatement[A]): ParameterValue = apply(a, s, p)
}

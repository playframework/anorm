package anorm

import java.sql.PreparedStatement

/** Prepared parameter value. */
sealed trait ParameterValue extends Show {

  /**
   * Returns SQL fragment (with '?' placeholders) for the parameter
   * at specified position in given tokenized statement.
   */
  def toSql: (String, Int)
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

final class DefaultParameterValue[A](
  val value: A, s: ToSql[A], toStmt: ToStatement[A])
    extends ParameterValue with ParameterValue.Wrapper[A] {

  lazy val toSql: (String, Int) =
    if (s == null) ("?" -> 1) else s.fragment(value)

  def set(s: PreparedStatement, i: Int) = toStmt.set(s, i, value)

  lazy val show = s"$value"
  override lazy val toString = s"ParameterValue($value)"
  override lazy val hashCode = value.hashCode

  override def equals(that: Any) = that match {
    case o: DefaultParameterValue[A] => (o.value == value)
    case _ => false
  }
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
    case _ => new DefaultParameterValue(v, s, toStmt)
  }

  implicit def toParameterValue[A](a: A)(implicit s: ToSql[A] = null, p: ToStatement[A]): ParameterValue = apply(a, s, p)
}

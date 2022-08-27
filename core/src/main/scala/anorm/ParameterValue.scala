package anorm

import java.sql.PreparedStatement

/** Prepared parameter value. */
@annotation.implicitNotFound("Wrapper not found for the parameter value")
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
}

final class DefaultParameterValue[A](val value: A, s: ToSql[A], toStmt: ToStatement[A])
    extends ParameterValue
    with ParameterValue.Wrapper[A] {

  lazy val toSql: (String, Int) = {
    @SuppressWarnings(Array("NullParameter"))
    def to = if (s == null) "?" -> 1 else s.fragment(value)

    to
  }

  def set(s: PreparedStatement, i: Int) = toStmt.set(s, i, value)

  lazy val show              = s"$value"
  override lazy val toString = s"ParameterValue($value)"
  override lazy val hashCode = value.hashCode

  override def equals(that: Any) = that match {
    case o: DefaultParameterValue[_] => o.value == value
    case _                           => false
  }
}

/**
 * Value factory for parameter.
 *
 * {{{
 * import anorm._
 *
 * val param = ParameterValue("str", null, implicitly[ToStatement[String]])
 *
 * SQL("...").onParams(param)
 * }}}
 */
object ParameterValue {
  import scala.language.implicitConversions

  private[anorm] trait Wrapper[T] { def value: T }

  @throws[IllegalArgumentException]("if value `v` is null whereas `toStmt` is marked with [[anorm.NotNullGuard]]")
  @SuppressWarnings(Array("NullParameter"))
  @inline def apply[A](v: A, s: ToSql[A], toStmt: ToStatement[A]): ParameterValue =
    (v, toStmt) match {
      case (null, _: NotNullGuard) => throw new IllegalArgumentException()
      case _                       => new DefaultParameterValue(v, s, toStmt)
    }

  @deprecated("Use an instance of `ToParameterValue`", "2.5.4")
  def toParameterValue[A](a: A)(implicit s: ToSql[A] = ToSql.missing, p: ToStatement[A]): ParameterValue =
    apply(a, s, p)

  implicit def from[A](a: A)(implicit c: ToParameterValue[A]): ParameterValue = c(a)
}

@annotation.implicitNotFound(
  "No converter found for type ${A} to `ParameterValue`; Please define appropriate `ToParameter` and `ParameterMetaData`."
)
sealed trait ToParameterValue[A] extends (A => ParameterValue) {

  /** Returns the parameter value corresponding to the given value */
  def apply(value: A): ParameterValue
}

object ToParameterValue {
  private class Default[A](s: ToSql[A], p: ToStatement[A]) extends ToParameterValue[A] {
    def apply(value: A): ParameterValue = ParameterValue(value, s, p)
  }

  implicit def apply[A](implicit s: ToSql[A] = ToSql.missing, p: ToStatement[A]): ToParameterValue[A] =
    new Default[A](s, p)
}

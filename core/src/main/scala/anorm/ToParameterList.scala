package anorm

/** Convert to a list of [[NamedParameter]] */
@annotation.implicitNotFound("No converter to SQL parameters for the type ${A}: `anorm.ToParameterList[${A}]` required")
trait ToParameterList[A] extends (A => List[NamedParameter]) {
  def apply(value: A): List[NamedParameter]
}

/** Companion/factory for [[ToParameterList]]. */
object ToParameterList {
  private class FunctionalToParameterList[A](f: A => List[NamedParameter]) extends ToParameterList[A] {
    def apply(value: A) = f(value)
  }

  /**
   * Functional factory.
   *
   * {{{
   * import anorm.{ NamedParameter, ToParameterList }
   *
   * case class Bar(v: String)
   *
   * implicit def toBar: ToParameterList[Bar] = ToParameterList[Bar] { bar =>
   *   List(anorm.NamedParameter.namedWithString("v" -> bar.v))
   * }
   * }}}
   */
  def apply[A](f: A => List[NamedParameter]): ToParameterList[A] =
    new FunctionalToParameterList(f)

  /**
   * Returns the list of [[NamedParameter]] of any `A` value.
   */
  def from[A](value: A)(implicit toParams: ToParameterList[A]): Seq[NamedParameter] = toParams(value)

  /**
   * Returns an instance producing an empty list of parameters.
   */
  def empty[A] = apply[A] { _ => List.empty }
}

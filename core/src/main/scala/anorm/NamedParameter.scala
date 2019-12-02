package anorm

/** Applied named parameter. */
final case class NamedParameter(name: String, value: ParameterValue) {
  lazy val tupled: (String, ParameterValue) = (name, value)
}

/**
 * Companion object for applied named parameter.
 *
 * @define namedWithString Conversion to use tuple, with first element being name of parameter as string.
 * @define namedWithSymbol Conversion to use tuple, with first element being symbolic name or parameter.
 */
object NamedParameter {
  import scala.language.implicitConversions

  /**
   * $namedWithString
   *
   * {{{
   * import anorm.{ NamedParameter, ParameterValue }
   *
   * def foo(pv: ParameterValue): NamedParameter = "name" -> pv
   * }}}
   */
  implicit def namedWithString(t: (String, ParameterValue)): NamedParameter = NamedParameter(t._1, t._2)

  /**
   * $namedWithString
   *
   * {{{
   * import anorm.NamedParameter
   *
   * val p: NamedParameter = "name" -> 1L
   * }}}
   */
  implicit def namedWithString[V](t: (String, V))(implicit c: ToParameterValue[V]): NamedParameter = NamedParameter(t._1, c(t._2))

  @deprecated("Use `string` based in `ToParameterValue`", "2.5.4")
  def string[V](t: (String, V))(implicit c: V => ParameterValue): NamedParameter = NamedParameter(t._1, c(t._2))

  /**
   * $namedWithSymbol
   *
   * {{{
   * import anorm.{ NamedParameter, ParameterValue }
   *
   * def foo(pv: ParameterValue): NamedParameter = 'name -> pv
   * }}}
   */
  implicit def namedWithSymbol(t: (Symbol, ParameterValue)): NamedParameter =
    NamedParameter(t._1.name, t._2)

  /**
   * $namedWithSymbol
   *
   * {{{
   * import anorm.NamedParameter
   *
   * val p: NamedParameter = 'name -> 1L
   * }}}
   */
  implicit def namedWithSymbol[V](t: (Symbol, V))(implicit c: ToParameterValue[V]): NamedParameter = NamedParameter(t._1.name, c(t._2))

  @deprecated("Use `string` based in `ToParameterValue`", "2.5.4")
  def symbol[V](t: (Symbol, V))(implicit c: V => ParameterValue): NamedParameter = NamedParameter(t._1.name, c(t._2))

}

package anorm

private[anorm] sealed trait StatementToken

private[anorm] case object PercentToken extends StatementToken {
  override val toString = "%"
}

private[anorm] case class StringToken(value: String) extends StatementToken {
  override lazy val toString = s""""${value.replaceAll("\"", "\\\"")}""""
}

private[anorm] object StatementToken {
  implicit object StatementTokenShowMaker extends Show.Maker[StatementToken] {
    def apply(token: StatementToken): Show = token match {
      case StringToken(value) => new StringShow(value)
      case _ => new StringShow(token.toString)
    }
  }
}

/**
 * @param prepared Already prepared tokens, not requiring to rewrite placeholder.
 * @param placeholder Optional placeholder (name), after already prepared tokens
 */
private[anorm] class TokenGroup(
  val prepared: List[StatementToken],
  val placeholder: Option[String]) extends Product with Serializable {

  def copy(prepared: List[StatementToken] = this.prepared, placeholder: Option[String] = this.placeholder): TokenGroup = new TokenGroup(prepared, placeholder)

  val productArity = 2

  @SuppressWarnings(Array("MethodReturningAny"))
  def productElement(n: Int): Any = n match {
    case 1 => prepared
    case 2 => placeholder
  }

  def canEqual(that: Any): Boolean = that match {
    case _: TokenGroup => true
    case _ => false
  }

  override def equals(that: Any): Boolean = that match {
    case other: TokenGroup =>
      (prepared, placeholder).equals(other.prepared -> other.placeholder)

    case _ => false
  }

  override def hashCode: Int = (prepared, placeholder).hashCode

  override lazy val toString = s"TokenGroup($prepared, $placeholder)"
}

object TokenGroup extends scala.runtime.AbstractFunction2[List[StatementToken], Option[String], TokenGroup] {

  def apply(prepared: List[StatementToken], placeholder: Option[String]): TokenGroup = new TokenGroup(prepared, placeholder)

  def unapply(group: TokenGroup): Option[(List[StatementToken], Option[String])] = Some(group.prepared -> group.placeholder)

  final class TokenGroupShow(group: TokenGroup) extends Show {
    def show: String = group.prepared.map(Show.mkString(_)).
      mkString + group.placeholder.fold("")(s => s"{$s}")
  }

  implicit object ShowMaker extends Show.Maker[TokenGroup] {
    def apply(subject: TokenGroup): Show = new TokenGroupShow(subject)
  }
}

package anorm

trait Show {
  def show: String
}

private[anorm] sealed trait StatementToken
private[anorm] case object PercentToken extends StatementToken

private[anorm] case class StringToken(value: String) extends StatementToken {
  override lazy val toString = s""""${value.replaceAll("\"", "\\\"")}""""
}

/**
 * @param prepared Already prepared tokens, not requiring to rewrite placeholder.
 * @param placeholder Optional placeholder (name), after already prepared tokens
 */
private[anorm] case class TokenGroup(
  prepared: List[StatementToken], placeholder: Option[String])

/**
 * @param tokens Token groups
 * @param names Binding names of parsed placeholders
 */
private[anorm] case class TokenizedStatement(
  tokens: List[TokenGroup], names: List[String])

private[anorm] object TokenizedStatement {
  import java.util.StringTokenizer
  import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter
  import scala.reflect.macros.Context
  import scala.language.experimental.macros

  /** Returns empty tokenized statement. */
  lazy val empty = TokenizedStatement(Nil, Nil)

  /** String interpolation to tokenize statement. */
  def stringInterpolation[T](parts: Seq[String], params: Seq[T with Show]): (TokenizedStatement, Map[String, T]) = macro tokenizeImpl[T]

  /** Tokenization macro */
  def tokenizeImpl[T: c.WeakTypeTag](c: Context)(parts: c.Expr[Seq[String]], params: c.Expr[Seq[T with Show]]): c.Expr[(TokenizedStatement, Map[String, T])] =
    c.universe.reify(tokenize(Iterator[String](), Nil, parts.splice,
      params.splice, Nil, Nil, Map.empty[String, T]))

  @annotation.tailrec
  private[anorm] def tokenize[T](ti: Iterator[String], tks: List[StatementToken], parts: Seq[String], ps: Seq[T with Show], gs: List[TokenGroup], ns: List[String], m: Map[String, T]): (TokenizedStatement, Map[String, T]) = if (ti.hasNext) ti.next match {
    case s: String =>
      tokenize(ti, StringToken(s) :: tks, parts, ps, gs, ns, m)
    case _ => /* should not occur */ tokenize(ti, tks, parts, ps, gs, ns, m)
  }
  else {
    if (tks.nonEmpty) {
      gs match {
        case prev :: groups => ps.headOption match {
          case Some(v) => prev match {
            case TokenGroup(StringToken(str) :: gts, pl) if (
              str endsWith "#" /* escaped part */ ) =>

              val before = if (str == "#") gts else {
                StringToken(str dropRight 1) :: gts
              }
              val ng = TokenGroup((tks ::: StringToken(v.show) ::
                before), pl)

              tokenize(ti, tks.tail, parts, ps.tail, (ng :: groups), ns, m)

            case _ =>
              val ng = TokenGroup(tks, None)
              val n = '_'.toString + ns.size
              tokenize(ti, tks.tail, parts, ps.tail,
                (ng :: prev.copy(placeholder = Some(n)) :: groups),
                (n :: ns), m + (n -> v))
          }
          case _ =>
            sys.error(s"No parameter value for placeholder: ${gs.size}")
        }
        case _ => tokenize(ti, tks.tail, parts, ps,
          List(TokenGroup(tks, None)), ns, m)
      }
    } else parts.headOption match {
      case Some(part) =>
        val it = List(part).iterator.map(_.toString)

        if (!it.hasNext /* empty */ ) {
          tokenize(it, List(StringToken("")), parts.tail, ps, gs, ns, m)
        } else tokenize(it, tks, parts.tail, ps, gs, ns, m)

      case _ =>
        val groups = ((gs match {
          case TokenGroup(List(StringToken("")), None) :: tgs => tgs // trim end
          case _ => gs
        }) map {
          case TokenGroup(pr, pl) => TokenGroup(pr.reverse, pl)
        }).reverse

        TokenizedStatement(groups, ns.reverse) -> m
    }
  }
}

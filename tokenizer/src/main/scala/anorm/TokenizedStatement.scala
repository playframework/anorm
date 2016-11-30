package anorm

/**
 * @param tokens the token groups
 * @param names the binding names of parsed placeholders
 */
private[anorm] case class TokenizedStatement(
  tokens: List[TokenGroup], names: List[String])

private[anorm] object TokenizedStatement {
  import java.util.StringTokenizer
  import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter
  import scala.language.experimental.macros
  import scala.reflect.macros.whitebox

  /** Returns empty tokenized statement. */
  lazy val empty = TokenizedStatement(Nil, Nil)

  /** String interpolation to tokenize statement. */
  def stringInterpolation[T](parts: Seq[String], params: Seq[T with Show]): (TokenizedStatement, Map[String, T]) = macro tokenizeImpl[T]

  /** Tokenization macro */
  def tokenizeImpl[T: c.WeakTypeTag](c: whitebox.Context)(parts: c.Expr[Seq[String]], params: c.Expr[Seq[T with Show]]): c.Expr[(TokenizedStatement, Map[String, T])] =
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

  final class TokenizedStatementShow(
      subject: TokenizedStatement) extends Show {
    def show = subject.tokens.map(Show.mkString(_)).mkString
  }

  implicit object ShowMaker extends Show.Maker[TokenizedStatement] {
    def apply(subject: TokenizedStatement): Show =
      new TokenizedStatementShow(subject)
  }
}

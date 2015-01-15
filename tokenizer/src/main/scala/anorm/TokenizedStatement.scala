package anorm

import scala.util.{ Failure, Try, Success => TrySuccess }

trait Show {
  def show: String
}

private[anorm] sealed trait StatementToken
private[anorm] case class StringToken(value: String) extends StatementToken
private[anorm] case object PercentToken extends StatementToken

private[anorm] case class TokenGroup(
  /** Already prepared tokens, not requiring to rewrite placeholder. */
  prepared: List[StatementToken],

  /** Optional placeholder (name), after already prepared tokens */
  placeholder: Option[String])

private[anorm] case class TokenizedStatement(
  /** Token groups */
  tokens: List[TokenGroup],

  /** Binding names of parsed placeholders */
  names: List[String])

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
    case "%" => tokenize(ti, PercentToken :: tks, parts, ps, gs, ns, m)
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

              val before = if (str == "#") gts.reverse else {
                StringToken(str dropRight 1) :: gts.reverse
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
        val it = new StringTokenizer(part, "%", true).
          asScala.map(_.toString)

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

  /**
   * Rewrites next placeholder in statement, with fragment using
   * [[java.sql.PreparedStatement]] syntax (with one or more '?').
   *
   * @param stmt Tokenized statement
   * @param frag Statement fragment
   * @return Rewritten statement
   *
   * {{{
   * Sql.rewrite("SELECT * FROM Test WHERE cat IN (%s)", "?, ?")
   * // Some("SELECT * FROM Test WHERE cat IN (?, ?)")
   * }}}
   */
  def rewrite(stmt: TokenizedStatement, frag: String): Try[TokenizedStatement] = stmt.tokens match {
    case TokenGroup(pr, Some(pl)) :: gs =>
      val prepared = pr :+ StringToken(frag)
      TrySuccess(TokenizedStatement(gs match {
        case TokenGroup(x, y) :: ts => TokenGroup(prepared ++ x, y) :: ts
        case _ => TokenGroup(prepared, None) :: Nil
      }, stmt.names))

    case _ => Failure(new Exception("No more placeholder"))
  }

  /** Returns statement as SQL string. */
  def toSql(stmt: TokenizedStatement): Try[String] = stmt.tokens match {
    case TokenGroup(_, Some(pl)) :: _ =>
      Failure(new IllegalStateException(s"Placeholder not prepared: $pl"))

    case TokenGroup(pr, None) :: Nil => TrySuccess(pr.foldLeft("") {
      case (sql, StringToken(t)) => sql + t
      case (sql, PercentToken) => sql + '%'
      case (sql, _) => sql
    })

    case _ =>
      Failure(new IllegalStateException(s"Unexpected statement: $stmt"))
  }
}

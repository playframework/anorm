/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package anorm

import scala.util.Try
import scala.language.postfixOps
import scala.util.parsing.combinator._

/** Parser for SQL statement. */
object SqlStatementParser extends JavaTokenParsers {
  /**
   * Returns tokenized statement and associated parameter names.
   * Extracts parameter names from {placeholder}s.
   *
   * {{{
   * import anorm.SqlStatementParser.parse
   *
   * val parsed: (String, List[String]) =
   *   parse("SELECT * FROM schema.table WHERE name = {name} AND cat = ?")
   * // parsed ==
   * //   ("SELECT * FROM schema.table WHERE name = ? AND cat = ?" ->
   * //    List("name"))
   * }}}
   */
  def parse(sql: String): Try[TokenizedStatement] = Try(parse(instr, sql).get)

  private val simpleLiteral: Parser[StringToken] =
    "[^'^{^\r^\n]+".r ^^ { StringToken(_) }

  private val instr: Parser[TokenizedStatement] = {
    @inline def normalize(t: StatementToken): Option[TokenGroup] = t match {
      case StringToken("") => Option.empty[TokenGroup]
      case StringToken(s) if (s.trim == "") =>
        Some(TokenGroup(List(StringToken(" ")), None))

      case _ => Some(TokenGroup(List(t), None))
    }

    "[ \t\r\n]*".r ~> rep(simpleLiteral.map(normalize) |
      quotedLiteral.map(Some(_)) | variable.map(Some(_)) |
      skipped.map(normalize)) ^^ {
      _.foldLeft(List.empty[TokenGroup] -> List.empty[String]) {
        case ((TokenGroup(ts, None) :: groups, ns),
          Some(TokenGroup(Nil, Some(n)))) =>
          (TokenGroup(ts, Some(n)) :: groups) -> (n :: ns)

        case ((TokenGroup(a, None) :: groups, ns), Some(TokenGroup(b, None))) =>
          (TokenGroup(a ::: b, None) :: groups) -> ns // merge groups

        case ((gs, ns), Some(g)) => (g :: gs) -> ns
        case ((gs, ns), _) => gs -> ns
      }
    } map {
      case (TokenGroup(List(StringToken(" ")), None) :: gs, ns) =>
        TokenizedStatement(gs.reverse, ns.reverse) // trim end #1

      case (TokenGroup(ts, pl) :: gs, ns) if ( // trim end #2
        ts.lastOption.filter(_ == StringToken(" ")).isDefined) =>
        TokenizedStatement((TokenGroup(ts.dropRight(1), pl) :: gs).reverse,
          ns.reverse)

      case (gs, ns) => TokenizedStatement(gs.reverse, ns.reverse)
    }
  }

  private val variable: Parser[TokenGroup] =
    "{" ~> (ident ~ (("." ~> ident)?)) <~ "}" ^^ {
      case i1 ~ i2 => TokenGroup(Nil, Some(i1 + i2.fold("")("." + _)))
    }

  private val reserved: Parser[PercentToken.type] =
    "%".r ^^ { _ => PercentToken }

  private lazy val quotedLiteral: Parser[TokenGroup] = {
    val simpleQuoted: Parser[StringToken] = "([^'^%])+".r ^^ { StringToken(_) }

    "'" ~> rep(reserved | simpleQuoted) <~ "'" ^^ { ts =>
      TokenGroup(StringToken("'") :: (ts :+ StringToken("'")), None)
    }
  }

  private val skipped: Parser[StringToken] =
    """[\r\n]""".r ^^ { _ => StringToken("") }

  override def skipWhitespace = false
}

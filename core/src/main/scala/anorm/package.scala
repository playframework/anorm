/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

import java.util.StringTokenizer
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter

/**
 * Anorm API
 *
 * Use the SQL method to start an SQL query
 *
 * {{{
 * import anorm._
 *
 * SQL("Select 1")
 * }}}
 */
package object anorm {
  import scala.language.implicitConversions

  /** Structural type for timestamp wrapper. */
  type TimestampWrapper1 = { def getTimestamp: java.sql.Timestamp }

  // TODO: Review implicit usage there 
  // (add explicit functions on SqlQuery?)
  implicit def sqlToSimple(sql: SqlQuery): SimpleSql[Row] = sql.asSimple

  implicit def implicitID[ID](id: Id[ID]): ID = id.id

  /**
   * Creates an SQL query with given statement.
   * @param stmt SQL statement
   *
   * {{{
   * val query = SQL("SELECT * FROM Country")
   * }}}
   */
  def SQL(stmt: String): SqlQuery =
    SqlStatementParser.parse(stmt).map(ts => SqlQuery.prepare(ts, ts.names)).get

  /**
   * Creates an SQL query using String Interpolation feature.
   * It is a 1-step alternative for SQL("...").on(...) functions.
   *
   * {{{
   * SQL"""
   *   UPDATE computer SET name = \\${computer.name},
   *   introduced = \\${computer.introduced},
   *   discontinued = \\${computer.discontinued},
   *   company_id = \\${computer.companyId}
   *   WHERE id = \\$id
   * """.executeUpdate()
   * }}}
   */
  implicit class SqlStringInterpolation(val sc: StringContext) extends AnyVal {
    def SQL(args: ParameterValue*) = {
      val (ts, ps) = TokenizedStatement.stringInterpolation(sc.parts, args)
      SimpleSql(SqlQuery.prepare(ts, ts.names), ps, RowParser(Success(_)))
    }
  }

  @annotation.tailrec
  private[anorm] def tokenize(ti: Iterator[Any], tks: List[StatementToken], parts: Seq[String], ps: Seq[ParameterValue], gs: List[TokenGroup], ns: List[String], m: Map[String, ParameterValue]): (TokenizedStatement, Map[String, ParameterValue]) = if (ti.hasNext) ti.next match {
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
        val it = new StringTokenizer(part, "%", true).asScala

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

  /** Activable features */
  object features {

    /**
     * Conversion for parameter with untyped named.
     *
     * {{{
     * // For backward compatibility
     * import anorm.features.parameterWithUntypedName
     *
     * val untyped: Any = "name"
     * SQL("SELECT * FROM Country WHERE {p}").on(untyped -> "val")
     * }}}
     */
    @deprecated(
      message = "Use typed name for parameter, either string or symbol",
      since = "2.3.0")
    implicit def parameterWithUntypedName[V](t: (Any, V))(implicit c: V => ParameterValue): NamedParameter = NamedParameter(t._1.toString, c(t._2))

    /**
     * Unsafe conversion from untyped value to statement parameter.
     * Value will be passed using setObject.
     *
     * It's not recommanded to use it as it can hide conversion issue.
     *
     * {{{
     * // For backward compatibility
     * import anorm.features.anyToStatement
     *
     * val d = new java.util.Date()
     * val params: Seq[NamedParameter] = Seq("mod" -> d, "id" -> "idv")
     * // Values as Any as heterogenous
     *
     * SQL("UPDATE item SET last_modified = {mod} WHERE id = {id}").
     *   on(params:_*)
     * // date and string passed with setObject, rather than
     * // setDate and setString.
     * }}}
     */
    @deprecated(
      message = "Do not passed parameter as untyped/Any value",
      since = "2.3.0")
    implicit def anyToStatement[T] = new ToStatement[T] {
      def set(s: java.sql.PreparedStatement, i: Int, any: T): Unit =
        s.setObject(i, any)
    }
  }
}

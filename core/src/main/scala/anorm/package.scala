/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

import java.sql.SQLException
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

  object TimestampWrapper1 {
    import scala.language.reflectiveCalls

    @SuppressWarnings(Array("AsInstanceOf"))
    def unapply(that: Any): Option[java.sql.Timestamp] = try {
      Some(that.asInstanceOf[TimestampWrapper1].getTimestamp)
    } catch {
      case _: NoSuchMethodException => None
    }
  }

  /** Structural type for oracle.sql.TIMESTAMP wrapper. */
  type TimestampWrapper2 = { def timestampValue: java.sql.Timestamp }

  object TimestampWrapper2 {
    import scala.language.reflectiveCalls

    @SuppressWarnings(Array("AsInstanceOf"))
    def unapply(that: Any): Option[java.sql.Timestamp] = try {
      Some(that.asInstanceOf[TimestampWrapper2].timestampValue)
    } catch {
      case _: NoSuchMethodException => None
      case _: SQLException => None
    }
  }

  /** Structural type for oracle.sql.ROWID wrapper. */
  type StringWrapper2 = { def stringValue: String }

  object StringWrapper2 {
    import scala.language.reflectiveCalls

    @SuppressWarnings(Array("AsInstanceOf"))
    def unapply(that: Any): Option[String] = try {
      Some(that.asInstanceOf[StringWrapper2].stringValue)
    } catch {
      case _: NoSuchMethodException => None
      case _: SQLException => None
    }
  }

  // TODO: Review implicit usage there
  // (add explicit functions on SqlQuery?)
  implicit def sqlToSimple(sql: SqlQuery): SimpleSql[Row] = sql.asSimple

  /**
   * Creates an SQL query with given statement.
   * @param stmt SQL statement
   *
   * {{{
   * val query = SQL("SELECT * FROM Country")
   * }}}
   */
  @SuppressWarnings(Array("MethodNames", "TryGet" /* TODO: Make it safer */ ))
  def SQL(stmt: String): SqlQuery = SqlStatementParser.parse(stmt).
    map(ts => SqlQuery.prepare(ts, ts.names)).get

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
  private[anorm] def tokenize(ti: Iterator[Any], tks: List[StatementToken], parts: Seq[String], ps: Seq[ParameterValue], gs: Seq[TokenGroup], ns: Seq[String], m: Map[String, ParameterValue]): (TokenizedStatement, Map[String, ParameterValue]) = if (ti.hasNext) ti.next match {
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
                (n +: ns), m + (n -> v))
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

  // Optimized resource typeclass not using reflection
  object StatementResource
    extends resource.Resource[java.sql.PreparedStatement] {

    def close(stmt: java.sql.PreparedStatement) = stmt.close()

    @deprecated("Deprecated by Scala-ARM upgrade", "2.5.4")
    def fatalExceptions = Seq[Class[_]](classOf[Exception])
  }

  // Optimized resource typeclass not using reflection
  object ResultSetResource extends resource.Resource[java.sql.ResultSet] {
    def close(rs: java.sql.ResultSet) = rs.close()

    @deprecated("Deprecated by Scala-ARM upgrade", "2.5.4")
    def fatalExceptions = Seq[Class[_]](classOf[Exception])
  }

  /** Activable features */
  object features {}
}

/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

import java.util.StringTokenizer

import java.sql.{ PreparedStatement, ResultSet, SQLException }
import java.lang.reflect.InvocationTargetException

import scala.reflect.ClassTag

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
package object anorm extends PackageCompat {

  /** Structural type for timestamp wrapper. */
  type TimestampWrapper1 = { def getTimestamp: java.sql.Timestamp }

  object TimestampWrapper1 {
    import scala.language.reflectiveCalls

    @SuppressWarnings(Array("AsInstanceOf"))
    def unapply(that: Any): Option[java.sql.Timestamp] = try {
      Some(that.asInstanceOf[TimestampWrapper1].getTimestamp)
    } catch {
      case _: NoSuchMethodException =>
        None

      case ie: InvocationTargetException => {
        val cause = ie.getCause

        if (cause != null) {
          throw cause
        }

        throw ie
      }
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
      case _: SQLException          => None

      case ie: InvocationTargetException => {
        val cause = ie.getCause

        if (cause != null) {
          throw cause
        }

        throw ie
      }
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
      case _: SQLException          => None

      case ie: InvocationTargetException => {
        val cause = ie.getCause

        if (cause != null) {
          throw cause
        }

        throw ie
      }
    }
  }

  /**
   * Creates an SQL query with given statement.
   * @param stmt SQL statement
   *
   * {{{
   * val query = anorm.SQL("SELECT * FROM Country")
   * }}}
   */
  @SuppressWarnings(Array("MethodNames", "TryGet" /* TODO: Make it safer */ ))
  def SQL(stmt: String): SqlQuery = SqlStatementParser.parse(stmt).map(ts => SqlQuery.prepare(ts, ts.names)).get

  /**
   * Creates an SQL query using String Interpolation feature.
   * It is a 1-step alternative for SQL("...").on(...) functions.
   *
   * {{{
   * import java.util.Date
   * import java.sql.Connection
   *
   * import anorm._
   *
   * case class Computer(
   *   name: String,
   *   introduced: Date,
   *   discontinued: Date,
   *   companyId: String)
   *
   * def foo(computer: Computer, id: String)(implicit con: Connection) =
   *   SQL"""
   *     UPDATE computer SET name = \\${computer.name},
   *     introduced = \\${computer.introduced},
   *     discontinued = \\${computer.discontinued},
   *     company_id = \\${computer.companyId}
   *     WHERE id = \\$id
   *   """.executeUpdate()
   * }}}
   */
  implicit class SqlStringInterpolation(val sc: StringContext) extends AnyVal {
    def SQL(args: ParameterValue*) = {
      val (ts, ps) = TokenizedStatement.stringInterpolation(sc.parts, args)
      SimpleSql(SqlQuery.prepare(ts, ts.names), ps, RowParser(Success(_)))
    }
  }

  @annotation.tailrec
  private[anorm] def tokenize(
      ti: Iterator[Any],
      tks: List[StatementToken],
      parts: Seq[String],
      ps: Seq[ParameterValue],
      gs: Seq[TokenGroup],
      ns: Seq[String],
      m: Map[String, ParameterValue]
  ): (TokenizedStatement, Map[String, ParameterValue]) = if (ti.hasNext) ti.next() match {
    case "%" => tokenize(ti, PercentToken :: tks, parts, ps, gs, ns, m)
    case s: String =>
      tokenize(ti, StringToken(s) :: tks, parts, ps, gs, ns, m)
    case _ => /* should not occur */ tokenize(ti, tks, parts, ps, gs, ns, m)
  }
  else {
    if (tks.nonEmpty) {
      gs match {
        case prev :: groups =>
          ps.headOption match {
            case Some(v) =>
              prev match {
                case TokenGroup(StringToken(str) :: gts, pl) if str.endsWith("#") /* escaped part */ =>
                  val before =
                    if (str == "#") gts.reverse
                    else {
                      StringToken(str.dropRight(1)) :: gts.reverse
                    }
                  val ng = TokenGroup(
                    tks ::: StringToken(v.show) ::
                      before,
                    pl
                  )

                  tokenize(ti, tks.tail, parts, ps.tail, ng :: groups, ns, m)

                case _ =>
                  val ng = TokenGroup(tks, None)
                  val n  = '_'.toString + ns.size
                  tokenize(
                    ti,
                    tks.tail,
                    parts,
                    ps.tail,
                    ng :: prev.copy(placeholder = Some(n)) :: groups,
                    n +: ns,
                    m + (n -> v)
                  )
              }
            case _ =>
              sys.error(s"No parameter value for placeholder: ${gs.size}")
          }
        case _ => tokenize(ti, tks.tail, parts, ps, List(TokenGroup(tks, None)), ns, m)
      }
    } else
      parts.headOption match {
        case Some(part) =>
          val it = Compat.javaEnumIterator[java.lang.Object](new StringTokenizer(part, "%", true))

          if (!it.hasNext /* empty */ ) {
            tokenize(it, List(StringToken("")), parts.tail, ps, gs, ns, m)
          } else tokenize(it, tks, parts.tail, ps, gs, ns, m)

        case _ =>
          val groups = (gs match {
            case TokenGroup(List(StringToken("")), None) :: tgs => tgs // trim end
            case _                                              => gs
          }).collect { case TokenGroup(pr, pl) =>
            TokenGroup(pr.reverse, pl)
          }.reverse

          TokenizedStatement(groups, ns.reverse) -> m
      }
  }

  // Optimized resource typeclass not using reflection
  object StatementResource extends resource.Resource[PreparedStatement] {
    def close(stmt: PreparedStatement) = stmt.close()

    @deprecated("Deprecated by Scala-ARM upgrade", "2.5.4")
    def fatalExceptions = Seq[Class[_]](classOf[Exception])
  }

  private[anorm] lazy val statementClassTag =
    implicitly[ClassTag[PreparedStatement]]

  // Optimized resource typeclass not using reflection
  object ResultSetResource extends resource.Resource[ResultSet] {
    def close(rs: ResultSet) = rs.close()

    @deprecated("Deprecated by Scala-ARM upgrade", "2.5.4")
    def fatalExceptions = Seq[Class[_]](classOf[Exception])
  }

  private[anorm] lazy val resultSetClassTag = implicitly[ClassTag[ResultSet]]

  /** Activable features */
  object features {

    /**
     * Column conversion that will accept `Byte` and `Short` values to represent booleans.
     * This is useful if the underlying database or driver does not support boolean datatype.
     * For example, the MariaDB JDBC driver v3 will return TINYINT metadata even for columns declared as BOOLEAN.
     *
     * 0 is false, 1 is true, anything else is an error.
     *
     * Note that the column is not limited to byte or short, boolean types are transparently accepted as well.
     *
     * {{{
     * import anorm.features.columnByteToBoolean
     * }}}
     */

    implicit val columnByteToBoolean: Column[Boolean] = Column.columnByteToBoolean
  }
}

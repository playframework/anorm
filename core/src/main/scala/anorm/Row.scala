package anorm

import scala.util.{ Success => TrySuccess, Try }

trait Row {
  private[anorm] def metaData: MetaData

  /** Raw data */
  private[anorm] val data: List[Any]

  /**
   * Returns row as list of column values.
   *
   * {{{
   * // Row first column is string "str", second one is integer 2
   * def l(row: anorm.Row): List[Any] = row.asList
   * // l == List[Any]("str", 2)
   * }}}
   *
   * @see #as
   */
  lazy val asList: List[Any] = Compat
    .lazyZip(data, metaData.ms)
    .map { (v, m) =>
      if (m.nullable) Option(v) else v
    }
    .toList

  /**
   * Returns row as dictionary of value per column name
   *
   * {{{
   * // Row column named 'A' is string "str", column named 'B' is integer 2
   * def m(row: anorm.Row): Map[String, Any] = row.asMap
   * // l == Map[String, Any]("table.A" -> "str", "table.B" -> 2)
   * }}}
   *
   * @see #as
   */
  lazy val asMap: Map[String, Any] =
    Compat.toMap(Compat.lazyZip(data, metaData.ms)) {
      case (v, m) =>
        val k = m.column.qualified

        if (m.nullable) k -> Option(v) else k -> v
    }

  /**
   * Returns row as `T`.
   *
   * {{{
   * import anorm._, SqlParser.{ int, str }
   *
   * def foo(implicit con: java.sql.Connection) = {
   *  val parseOnlyFirstRow =
   *      SQL"SELECT * FROM Table".withResult(_.map(_.row.as(
   *       str("foo") ~ int(2) map {
   *         case a ~ b => b -> a
   *       })))
   *   // Either[List[Throwable], Option[Try[(Int, String)]]]
   *
   *   val optionalParseRes =
   *     parseOnlyFirstRow.right.map(_.flatMap(_.toOption))
   *     // Either[List[Throwable], Option[(Int, String)]]
   * }
   * }}}
   *
   * @param parser Row parser
   */
  def as[T](parser: RowParser[T]): Try[T] =
    parser(this).fold(_.toFailure, TrySuccess(_))

  /**
   * Returns parsed column.
   *
   * @param name Column name
   * @param c Column mapping
   *
   * {{{
   * import anorm._, Column.columnToString // mapping column to string
   *
   * val res = SQL("SELECT * FROM Test").map { row =>
   *   // string columns 'code' and 'label'
   *   row[String]("code") -> row[String]("label")
   * }
   * }}}
   */
  def apply[B](name: String)(implicit c: Column[B]): B =
    unsafeGet(SqlParser.get(name)(c))

  /**
   * Returns parsed column.
   *
   * @param position Column position from 1 to n
   * @param c Column mapping
   *
   * {{{
   * import anorm._, Column.columnToString // mapping column to string
   *
   * val res = SQL("SELECT * FROM Test").map { row =>
   *   row(1) -> row(2) // string columns #1 and #2
   * }
   * }}}
   */
  def apply[B](position: Int)(implicit c: Column[B]): B =
    unsafeGet(SqlParser.get(position)(c))

  @inline def unsafeGet[T](rowparser: => RowParser[T]): T =
    rowparser(this) match {
      case Success(v) => v
      case Error(err) => throw err.toFailure.exception
    }

  // Data per column name
  private lazy val columnsDictionary: Map[String, Any] =
    Compat.toMap(Compat.lazyZip(metaData.ms, data)) {
      case (m, v) =>
        m.column.qualified.toUpperCase -> v
    }

  // Data per column alias
  private lazy val aliasesDictionary: Map[String, Any] = {
    @annotation.tailrec
    def loop(meta: Seq[MetaDataItem], dt: List[Any], r: Map[String, Any]): Map[String, Any] = (meta, dt) match {
      case (m :: ms, d :: ds) => loop(ms, ds, m.column.alias.fold(r) { c => r + (c.toUpperCase -> d) })
      case _                  => r
    }

    loop(metaData.ms, data, Map.empty)
  }

  /**
   * Try to get data matching name.
   * @param a Column qualified name, or label/alias
   */
  private[anorm] def get(a: String): Either[SqlRequestError, (Any, MetaDataItem)] =
    Compat.rightFlatMap(metaData.get(a.toUpperCase).toRight(ColumnNotFound(a, this))) { m =>
      def d = if (a.indexOf(".") > 0) {
        // if expected to be a qualified (dotted) name
        columnsDictionary.get(m.column.qualified.toUpperCase).orElse(m.column.alias.flatMap(aliasesDictionary.get(_)))

      } else {
        m.column.alias
          .flatMap(a => aliasesDictionary.get(a.toUpperCase))
          .orElse(columnsDictionary.get(m.column.qualified.toUpperCase))
      }

      Compat.rightMap(d.toRight(ColumnNotFound(m.column.qualified, metaData.availableColumns))) { _ -> m }
    }

  /** Try to get data matching index. */
  private[anorm] def getIndexed(i: Int): Either[SqlRequestError, (Any, MetaDataItem)] =
    Compat.rightFlatMap(metaData.ms.lift(i).toRight(ColumnNotFound(s"#${i + 1}", metaData.availableColumns))) { m =>
      Compat.rightMap(data.lift(i).toRight(ColumnNotFound(m.column.qualified, metaData.availableColumns))) { _ -> m }
    }
}

/** Companion object for row. */
object Row {

  /**
   * Row extractor.
   *
   * {{{
   * import java.util.Locale
   *
   * import anorm._
   *
   * def l(implicit con: java.sql.Connection): Option[Locale] =
   *   SQL("Select name,population from Country").
   *     as(RowParser[Option[Locale]] {
   *       case Row("France", _) => Success(Some(Locale.FRANCE))
   *       case _ => Success(Option.empty[Locale])
   *     }.single)
   * }}}
   */
  def unapplySeq(row: Row): Option[List[Any]] = Some(row.asList)
}

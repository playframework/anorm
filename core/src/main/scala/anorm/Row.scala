package anorm

import scala.util.{ Try, Success => TrySuccess }
import scala.collection.breakOut

trait Row {
  private[anorm] def metaData: MetaData

  /** Raw data */
  private[anorm] val data: List[Any]

  /**
   * Returns row as list of column values.
   *
   * {{{
   * // Row first column is string "str", second one is integer 2
   * val l: List[Any] = row.asList
   * // l == List[Any]("str", 2)
   * }}}
   *
   * @see #as
   */
  lazy val asList: List[Any] = (data, metaData.ms).zipped.map { (v, m) =>
    if (m.nullable) Option(v) else v
  }

  /**
   * Returns row as dictionary of value per column name
   *
   * {{{
   * // Row column named 'A' is string "str", column named 'B' is integer 2
   * val m: Map[String, Any] = row.asMap
   * // l == Map[String, Any]("table.A" -> "str", "table.B" -> 2)
   * }}}
   *
   * @see #as
   */
  lazy val asMap: Map[String, Any] = (data, metaData.ms).zipped.map { (v, m) =>
    val k = m.column.qualified
    if (m.nullable) (k -> Option(v)) else k -> v
  }(breakOut)

  /**
   * Returns row as `T`.
   *
   * {{{
   * import anorm.SqlParser.{ int, str }
   *
   * val parseOnlyFirstRow =
   *   SQL"SELECT * FROM Table".withResult(_.map(_.row.as(
   *     str("foo") ~ int(2) map {
   *       case a ~ b => b -> a
   *     })))
   * // Either[List[Throwable], Option[Try[(Int, String)]]]
   *
   * val optionalParseRes =
   *   parseOnlyFirstRow.right.map(_.flatMap(_.toOption))
   *   // Either[List[Throwable], Option[(Int, String)]]
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
   * import anorm.Column.columnToString // mapping column to string
   *
   * val res: (String, String) = SQL("SELECT * FROM Test").map(row =>
   *   row("code") -> row("label") // string columns 'code' and 'label'
   * )
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
   * import anorm.Column.columnToString // mapping column to string
   *
   * val res: (String, String) = SQL("SELECT * FROM Test").map(row =>
   *   row(1) -> row(2) // string columns #1 and #2
   * )
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
    (metaData.ms, data).zipped.map((m, v) =>
      m.column.qualified.toUpperCase -> v)(breakOut)

  // Data per column alias
  private lazy val aliasesDictionary: Map[String, Any] = {
    @annotation.tailrec
    def loop(meta: Seq[MetaDataItem], dt: List[Any], r: Map[String, Any]): Map[String, Any] = (meta, dt) match {
      case (m :: ms, d :: ds) => loop(ms, ds,
        m.column.alias.fold(r) { c => r + (c.toUpperCase -> d) })
      case _ => r
    }

    loop(metaData.ms, data, Map.empty)
  }

  /**
   * Try to get data matching name.
   * @param a Column qualified name, or label/alias
   */
  private[anorm] def get(a: String): Either[SqlRequestError, (Any, MetaDataItem)] = for {
    m <- metaData.get(a.toUpperCase).toRight(ColumnNotFound(a, this)).right
    data <- {
      def d = if (a.indexOf(".") > 0) {
        // if expected to be a qualified (dotted) name
        columnsDictionary.get(m.column.qualified.toUpperCase).
          orElse(m.column.alias.flatMap(aliasesDictionary.get(_)))

      } else {
        m.column.alias.flatMap(a => aliasesDictionary.get(a.toUpperCase)).
          orElse(columnsDictionary.get(m.column.qualified.toUpperCase))
      }

      d.toRight(
        ColumnNotFound(m.column.qualified, metaData.availableColumns))
    }.right
  } yield (data, m)

  /** Try to get data matching index. */
  private[anorm] def getIndexed(i: Int): Either[SqlRequestError, (Any, MetaDataItem)] =
    for {
      m <- metaData.ms.lift(i).toRight(
        ColumnNotFound(s"#${i + 1}", metaData.availableColumns)).right

      d <- data.lift(i).toRight(
        ColumnNotFound(m.column.qualified, metaData.availableColumns)).right
    } yield (d, m)

}

/** Companion object for row. */
object Row {

  /**
   * Row extractor.
   *
   * {{{
   * import java.util.Locale
   *
   * val l: Option[Locale] =
   *   SQL("Select name,population from Country")().collect {
   *     case Row("France", _) => Some(Locale.FRANCE)
   *     case _ => None
   *   }
   * }}}
   */
  def unapplySeq(row: Row): Option[List[Any]] = Some(row.asList)
}

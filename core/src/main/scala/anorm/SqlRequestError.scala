package anorm

import scala.util.Failure

/** Anorm runtime exception */
final case class AnormException(message: String)
    extends Exception with scala.util.control.NoStackTrace {
  override def getMessage() = message
}

/** Error from processing SQL */
sealed trait SqlRequestError {
  def message: String

  /** Returns error as a failure. */
  def toFailure = Failure(AnormException(message))
}

/**
 * Error raised when the specified `column` cannot be found in results.
 *
 * @param column the name of the not found column
 * @param possibilities the names of the available columns
 */
case class ColumnNotFound(
    column: String, possibilities: List[String]) extends SqlRequestError {

  lazy val message = s"'$column' not found, available columns: " +
    possibilities.map(_.dropWhile(_ == '.')).mkString(", ")

  override lazy val toString = message
}

object ColumnNotFound {
  def apply(column: String, row: Row): ColumnNotFound =
    ColumnNotFound(column, row.metaData.availableColumns)
}

case class UnexpectedNullableFound(reason: String) extends SqlRequestError {
  lazy val message = s"UnexpectedNullableFound($reason)"
  override lazy val toString = message
}

case class SqlMappingError(reason: String) extends SqlRequestError {
  lazy val message = s"SqlMappingError($reason)"
  override lazy val toString = message
}

case class TypeDoesNotMatch(reason: String) extends SqlRequestError {
  lazy val message = s"TypeDoesNotMatch($reason)"
  override lazy val toString = message
}

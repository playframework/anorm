/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import scala.util.Failure

import scala.collection.immutable.Seq

/** Anorm runtime exception */
final case class AnormException(message: String) extends Exception with scala.util.control.NoStackTrace {
  override def getMessage() = message
}

/** Error from processing SQL */
sealed trait SqlRequestError {
  def message: String

  /** Returns error as a failure. */
  def toFailure = Failure(AnormException(message))
}

object SqlRequestError {
  def apply(cause: Throwable): SqlRequestError = new SqlRequestError {
    val message            = cause.getMessage
    override def toFailure = Failure(cause)
  }
}

/**
 * Error raised when the specified `column` cannot be found in results.
 *
 * @param column the name of the not found column
 * @param available the names of the available columns
 */
case class ColumnNotFound(
    column: String,
    @deprecatedName(Symbol("possibilities")) available: Seq[String]
) extends SqlRequestError {

  @deprecated("Use constructor with `available` sequence", "2.5.4")
  def this(column: String, possibilities: List[String]) =
    this(column, available = possibilities.toSeq)

  lazy val message = s"'$column' not found, available columns: " +
    available.map(_.dropWhile(_ == '.')).mkString(", ")

  @deprecated("Use `available`", "2.5.4")
  def possibilities = available.toList

  override lazy val toString = message
}

object ColumnNotFound {
  def apply(column: String, row: Row): ColumnNotFound =
    new ColumnNotFound(column, row.metaData.availableColumns)

  @deprecated("Use factory with `available` sequence", "2.5.4")
  def apply(column: String, possibilities: List[String]): ColumnNotFound =
    new ColumnNotFound(column, possibilities)
}

// TODO: No longer be SqlRequestError, but a ColumnError (new hierarchy)
case class UnexpectedNullableFound(reason: String) extends SqlRequestError {
  lazy val message           = s"UnexpectedNullableFound($reason)"
  override lazy val toString = message
}

case class SqlMappingError(reason: String) extends SqlRequestError {
  lazy val message           = s"SqlMappingError($reason)"
  override lazy val toString = message
}

// TODO: No longer be SqlRequestError, but a ColumnError (new hierarchy)
case class TypeDoesNotMatch(reason: String) extends SqlRequestError {
  lazy val message           = s"TypeDoesNotMatch($reason)"
  override lazy val toString = message
}

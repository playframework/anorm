package anorm

import java.sql.PreparedStatement

/** Sets value as statement parameter. */
@annotation.implicitNotFound(
  "Cannot set value of type ${A} as parameter on statement: `anorm.ToStatement[${A}] required`"
)
trait ToStatement[A] extends ToStatementBase[A]

object ToStatement extends ToStatementConversions {
  private class FunctionalToStatement[T](f: (PreparedStatement, Int, T) => Unit) extends ToStatement[T] {
    def set(s: PreparedStatement, index: Int, v: T): Unit = { f(s, index, v) }
  }

  /** Functional factory */
  def apply[T](set: (PreparedStatement, Int, T) => Unit): ToStatement[T] =
    new FunctionalToStatement[T](set)
}

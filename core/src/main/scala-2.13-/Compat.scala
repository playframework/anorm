package anorm

import java.util.{ Enumeration => JEnum }

import scala.collection.breakOut
import scala.collection.immutable.Map

private[anorm] object Compat {
  @inline def toMap[T, K, V](in: Traversable[T])(f: T => (K, V)): Map[K, V] =
    in.map(f)(breakOut)

  @inline def toFlatMap[T, K, V](in: Traversable[T])(f: T => Map[K, V]): Map[K, V] = in.flatMap(f)(breakOut)

  @inline def lazyZip[A, B](a: Iterable[A], b: Iterable[B]) = (a -> b).zipped

  @inline def collectToMap[T, K, V](in: Traversable[T])(f: PartialFunction[T, (K, V)]): Map[K, V] = in.collect(f)(breakOut)

  @inline def rightMap[L, R1, R2](e: Either[L, R1])(f: R1 => R2): Either[L, R2] = e.right.map(f)

  @inline def rightFlatMap[L1, L2 >: L1, R1, R2](e: Either[L1, R1])(f: R1 => Either[L2, R2]): Either[L2, R2] = e.right.flatMap(f)

  @inline def javaEnumIterator[T](e: JEnum[T]): Iterator[T] = {
    import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter
    e.asScala
  }

  @inline def mapValues[K, V1, V2](m: Map[K, V1])(f: V1 => V2) = m.mapValues(f)

  /** Scala 2.13 compatibility type alias */
  type Trav[T] = Traversable[T]

  /** Scala 2.13 compatibility type alias */
  type LazyLst[T] = Stream[T]

  @inline def LazyLst[T](values: T*): LazyLst[T] = Stream.empty[T] ++ values
}

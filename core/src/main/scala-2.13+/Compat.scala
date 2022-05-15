package anorm

import java.util.{ Enumeration => JEnum }

import scala.collection.immutable.Map

private[anorm] object Compat {
  @inline def toMap[T, K, V](in: Iterable[T])(f: T => (K, V)): Map[K, V] =
    in.view.map(f).to(Map)

  @inline def toFlatMap[T, K, V](in: Iterable[T])(f: T => IterableOnce[(K, V)]): Map[K, V] = in.view.flatMap(f).to(Map)

  @inline def lazyZip[A, B](a: Iterable[A], b: Iterable[B]) = a.lazyZip(b)

  @inline def collectToMap[T, K, V](in: Iterable[T])(f: PartialFunction[T, (K, V)]): Map[K, V] = in.view.collect(f).to(Map)

  @inline def rightMap[L, R1, R2](e: Either[L, R1])(f: R1 => R2): Either[L, R2] = e.map(f)

  @inline def rightFlatMap[L1, L2 >: L1, R1, R2](e: Either[L1, R1])(f: R1 => Either[L2, R2]): Either[L2, R2] = e.flatMap(f)

  @inline def javaEnumIterator[T](e: JEnum[T]): Iterator[T] = {
    import scala.jdk.CollectionConverters._
    e.asScala
  }

  @inline def mapValues[K, V1, V2](m: Map[K, V1])(f: V1 => V2) = m.view.mapValues(f)

  /** Scala 2.13 compatibility type alias */
  type Trav[T] = Iterable[T]

  /** Scala 2.13 compatibility type alias */
  type LazyLst[T] = LazyList[T]

  @inline def LazyLst[T](values: T*): LazyLst[T] = LazyList.empty[T] :++ values
}

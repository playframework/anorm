package anorm

import scala.collection.breakOut

import scala.collection.immutable.Map

private[anorm] object Compat {
  @inline def toMap[T, K, V](in: Traversable[T])(f: T => (K, V)): Map[K, V] =
    in.map(f)(breakOut)

  @inline def toFlatMap[T, K, V](in: Traversable[T])(f: T => Map[K, V]): Map[K, V] = in.flatMap(f)(breakOut)

  @inline def lazyZip[A, B](a: Iterable[A], b: Iterable[B]) = (a -> b).zipped

  @inline def collectToMap[T, K, V](in: Traversable[T])(f: PartialFunction[T, (K, V)]): Map[K, V] = in.collect(f)(breakOut)
}

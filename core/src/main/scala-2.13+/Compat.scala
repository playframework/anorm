package anorm

import scala.collection.immutable.Map

private[anorm] object Compat {
  @inline def toMap[T, K, V](in: Iterable[T])(f: T => (K, V)): Map[K, V] =
    in.view.map(f).to(Map)

  @inline def toFlatMap[T, K, V](in: Iterable[T])(f: T => IterableOnce[(K, V)]): Map[K, V] = in.view.flatMap(f).to(Map)

  @inline def lazyZip[A, B](a: Iterable[A], b: Iterable[B]) = a.lazyZip(b)

  @inline def collectToMap[T, K, V](in: Iterable[T])(f: PartialFunction[T, (K, V)]): Map[K, V] = in.view.collect(f).to(Map)
}

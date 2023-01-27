/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

private[anorm] object AkkaCompat {
  type Seq[T] = _root_.scala.collection.immutable.Seq[T]
}

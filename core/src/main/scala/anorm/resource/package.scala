/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import scala.util.Using

/** Inspired from scala-arm */
package object resource {

  private[anorm] def managed[A <: AutoCloseable](
      resource: => A
  ): ManagedResource[A] =
    new ManagedResource(
      new Continuation[A] {
        def apply[B](
            manager: Using.Manager,
            next: A => B
        ): B =
          next(manager(resource))
      }
    )
}

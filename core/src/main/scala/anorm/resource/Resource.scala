/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm.resource

import scala.util.Using

/** Inspired from scala-arm */
sealed trait Resource[A] { self =>
  protected def continuation: Continuation[A]

  private[resource] def run[B](manager: Using.Manager)(next: A => B): B =
    continuation(manager, next)

  def acquireFor[B](f: A => B): Either[List[Throwable], B] =
    Using
      .Manager { manager =>
        run(manager)(f)
      }
      .toEither
      .left
      .map(List(_))

  def acquireAndGet[B](f: A => B): B =
    acquireFor(f) match {
      case Right(value) => value
      case Left(errors) => throw errors.head
    }

  def map[B](f: A => B): ManagedResource[B] =
    new ManagedResource(
      new Continuation[B] {
        def apply[C](
            manager: Using.Manager,
            next: B => C
        ): C = self.run(manager)(a => next(f(a)))
      }
    )

  def flatMap[B](
      f: A => ManagedResource[B]
  ): ManagedResource[B] =
    new ManagedResource(
      new Continuation[B] {
        def apply[C](
            manager: Using.Manager,
            next: B => C
        ): C = self.run(manager)(a => f(a).run(manager)(next))
      }
    )
}

final class ManagedResource[A] private[resource] (
    protected val continuation: Continuation[A]
) extends Resource[A] { self =>

  def and[B](
      other: ManagedResource[B]
  ): ManagedResource[(A, B)] =
    new ManagedResource(
      new Continuation[(A, B)] {
        def apply[C](
            manager: Using.Manager,
            next: ((A, B)) => C
        ): C =
          self.run(manager) { a =>
            other.run(manager) { b =>
              next(a -> b)
            }
          }
      }
    )
}

private[resource] trait Continuation[A] {
  def apply[B](manager: Using.Manager, next: A => B): B
}

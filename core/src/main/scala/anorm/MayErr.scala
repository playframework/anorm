/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package anorm

// TODO: Remove

private[anorm] case class MayErr[+E, +A](toEither: Either[E, A]) {

  def flatMap[B, EE >: E](f: A => MayErr[EE, B]): MayErr[EE, B] =
    MayErr(toEither.right.flatMap(a => f(a).toEither))

  def map[B](f: A => B): MayErr[E, B] = MayErr(toEither.right.map(f))

  /**
   * Applies `f` if this is a failure or `s` if this is a successful value.
   *
   * @param f the function to apply on failure
   * @param s the function to apply on successful `A` value
   * @return the results of applying appropriate function
   */
  def fold[B](f: E => B, s: A => B): B = toEither.fold(f, s)

  /**
   * Returns successful value, or throws exception.
   */
  def get = toEither.fold(e =>
    throw new RuntimeException(toEither.toString), a => a)

}

private[anorm] object MayErr {
  import scala.language.implicitConversions

  implicit def eitherToError[E, EE >: E, A, AA >: A](e: Either[E, A]): MayErr[EE, AA] = MayErr[E, A](e)
}

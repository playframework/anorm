/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

/* No valid testkit for 2.11 */
private[anorm] object StreamTestKit {
  def assertAllStagesStopped[T](f: => T): T = f
}

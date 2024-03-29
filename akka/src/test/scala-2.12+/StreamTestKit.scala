/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import akka.stream.Materializer
import akka.stream.testkit.scaladsl.{ StreamTestKit => AkkaTestKit }

private[anorm] object StreamTestKit {
  def assertAllStagesStopped[T](f: => T)(implicit mat: Materializer): T =
    AkkaTestKit.assertAllStagesStopped(f)
}

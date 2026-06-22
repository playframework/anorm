/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import scala.collection.mutable.ListBuffer

import org.specs2.matcher.MatchResult

final class ResourceSpec extends org.specs2.mutable.Specification {
  "Resource management".title

  import resource._

  "ManagedResource" should {
    "acquireAndGet" >> {
      "propagate result" in withNoLeakState { state =>
        (managed(new TestResource("r1", state)).acquireAndGet(_.name) must_=== "r1").and {
          state.open must_=== 0
        }
      }

      "throw on failure" in withNoLeakState { state =>
        managed(new TestResource("r1", state))
          .acquireAndGet[String] { _ =>
            throw new RuntimeException("boom")
          } must throwA[RuntimeException]("boom")
      }
    }

    "acquireFor" >> {
      "acquire and release a resource" in withNoLeakState { state =>
        val result =
          managed(new TestResource("r1", state))
            .acquireFor(_.name)

        (result must beRight("r1")).and {
          state.list must_=== List(
            "acquire:r1",
            "close:r1"
          )
        }
      }

      "release resource when usage fails" in withNoLeakState { state =>
        val result =
          managed(new TestResource("r1", state))
            .acquireFor[String] { _ =>
              throw new RuntimeException("boom")
            }

        (result must beLeft).and {
          state.list must_=== List(
            "acquire:r1",
            "close:r1"
          )
        }
      }

      "return Left when closing resource fails" in withNoLeakState { state =>
        val result =
          managed(
            new TestResource(
              "r1",
              state,
              failOnClose = true
            )
          )
            .acquireFor(_.name)

        (result must beLeft).and {
          state.list must_=== List("acquire:r1", "close:r1")
        }
      }
    }

    "map value" >> {
      "while keeping lifecycle" in withNoLeakState { state =>
        val result =
          managed(new TestResource("r1", state))
            .map { resource =>
              state.events += "map"

              resource.name.toUpperCase
            }
            .acquireFor(identity)

        (result must beRight("R1")).and {
          state.list must_=== List(
            "acquire:r1",
            "map",
            "close:r1"
          )
        }
      }

      "close resources usage fails" in withNoLeakState { state =>
        val result =
          managed(new TestResource("r1", state))
            .map { resource =>
              state.events += "map"

              resource.name.toUpperCase
            }
            .acquireFor { _ =>
              throw new RuntimeException("boom")
            }

        (result must beLeft).and {
          state.list must_=== List(
            "acquire:r1",
            "map",
            "close:r1"
          )
        }
      }

      "close resources when flatMap fails" in withNoLeakState { state =>
        val result =
          managed(new TestResource("r1", state))
            .map[Int] { _ =>
              throw new RuntimeException("boom")
            }
            .acquireFor(identity)

        (result must beLeft).and {
          state.list must_=== List(
            "acquire:r1",
            "close:r1"
          )
        }
      }
    }

    "when flatMap" >> {
      "resources and close them in reverse order" in withNoLeakState { state =>
        val result =
          managed(new TestResource("r1", state))
            .flatMap { _ =>
              managed(new TestResource("r2", state))
            }
            .acquireFor(_.name)

        (result must beRight("r2")).and {
          state.list must_=== List(
            "acquire:r1",
            "acquire:r2",
            "close:r2",
            "close:r1"
          )
        }
      }

      "close resources usage fails" in withNoLeakState { state =>
        val result =
          managed(new TestResource("r1", state))
            .flatMap { _ =>
              managed(new TestResource("r2", state))
            }
            .acquireFor[String] { _ =>
              throw new RuntimeException("boom")
            }

        (result must beLeft).and {
          state.list must_=== List(
            "acquire:r1",
            "acquire:r2",
            "close:r2",
            "close:r1"
          )
        }
      }

      "close resources when flatMap fails" in withNoLeakState { state =>
        val result =
          managed(new TestResource("r1", state))
            .flatMap { _ =>
              throw new RuntimeException("boom")
              managed(new TestResource("r2", state))
            }
            .acquireFor(_.name)

        (result must beLeft).and {
          state.list must_=== List(
            "acquire:r1",
            "close:r1"
          )
        }
      }
    }

    "when combine using `and`" >> {
      "be successful" in withNoLeakState { state =>
        val result =
          managed(new TestResource("r1", state))
            .and(managed(new TestResource("r2", state)))
            .acquireFor {
              case (r1, r2) =>
                r1.name + r2.name
            }

        (result must beRight("r1r2")).and {
          state.list must_=== List(
            "acquire:r1",
            "acquire:r2",
            "close:r2",
            "close:r1"
          )
        }
      }

      "close all resources if fails" in withNoLeakState { state =>
        val result =
          managed(new TestResource("r1", state))
            .and(managed(new TestResource("r2", state)))
            .acquireFor[String] {
              case (_, _) =>
                throw new RuntimeException("boom")
            }

        (result must beLeft).and {
          state.list must_=== List(
            "acquire:r1",
            "acquire:r2",
            "close:r2",
            "close:r1"
          )
        }
      }
    }
  }

  // ---

  private def withNoLeakState[T](f: State => MatchResult[T]): MatchResult[_] = {
    val state = State()

    f(state).and {
      state.open must_=== 0
    }
  }

  private final case class State(
      events: ListBuffer[String] = ListBuffer.empty,
      var open: Int = 0
  ) {
    def list: List[String] = events.toList
  }

  private final class TestResource(
      val name: String,
      state: State,
      failOnClose: Boolean = false
  ) extends AutoCloseable {

    state.events += s"acquire:$name"
    state.open += 1

    override def close(): Unit = {
      state.events += s"close:$name"
      state.open -= 1

      if (failOnClose) {
        throw new RuntimeException(s"close:$name")
      }
    }
  }
}

# Anorm 2.6 Migration Guide

This is a guide for migrating from Anorm 2.5 to Anorm 2.6. If you need to migrate from an earlier version of Anorm then you must first follow the [Anorm 2.5 Migration Guide](https://github.com/playframework/anorm/blob/master/Migration25.md#anorm-25-migration-guide).

## Deprecation

The deprecated type `MayErr` is no longer part of the API.

The former `Column.nonNull` is updated from `nonNull[A](transformer: ((Any, MetaDataItem) => MayErr[SqlRequestError, A])): Column[A]` to `def nonNull[A](transformer: ((Any, MetaDataItem) => Either[SqlRequestError, A])): Column[A] = Column[A]`.

The deprecated operations `.list()`, `.single()` and `.singleOpt()` on SQL result (e.g. `SQL("...").list()`) are now removed, and must be respectively replaced by `.as(parser.*)`, `.as(parser.single)` and `.as(parser.singleOpt)`.

The `.getFilledStatement` has been removed.

The former streaming operation `.apply()` is now removed, and must be replaced by either `.fold`, `.foldWhile` or `.withResult`.

## Iteratees module

A new Anorm is available to ease the integration with [Play Iteratees](https://www.playframework.com/documentation/latest/Iteratees).

It can be added to your project using the following dependencies.

```
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "anorm-iteratee" % "ANORM_VERSION",
  "com.typesafe.play" %% "play-iteratees" % "ITERATEES_VERSION")
```

> For a Play application, as `play-iteratees` is provided there is no need to add this dependency.

Then the parsed results from Anorm can be turned into [`Enumerator`](https://www.playframework.com/documentation/latest/api/scala/index.html#play.api.libs.iteratee.Enumerator).

```scala
import java.sql.Connection
import scala.concurrent.ExecutionContext.Implicits.global
import anorm._
import play.api.libs.iteratee._

def resultAsEnumerator(implicit con: Connection): Enumerator[String] =
  Iteratees.from(SQL"SELECT * FROM Test", SqlParser.scalar[String])
```
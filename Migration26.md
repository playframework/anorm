# Anorm 2.6 Migration Guide

This is a guide for migrating from Anorm 2.5 to Anorm 2.6. If you need to migrate from an earlier version of Anorm then you must first follow the [Anorm 2.5 Migration Guide](https://github.com/playframework/anorm/blob/master/Migration25.md#anorm-25-migration-guide).

## Operations

The new operation `.executeInsert1` allows to select columns among the generated keys.

```scala
// Choose 'generatedCol' and 'colB' from the generatedKeys
val keys1 = SQL("INSERT INTO Test(x) VALUES ({x})").
  on("x" -> "y").executeInsert1("generatedCol", "colB")()

val keys2 = SQL("INSERT INTO Test(x) VALUES ({x})").
  on("x" -> "y").executeInsert1("generatedCol")(scalar[String].singleOpt)
```

## Deprecation

The deprecated type `MayErr` is no longer part of the API.

The former `Column.nonNull` is updated from `nonNull[A](transformer: ((Any, MetaDataItem) => MayErr[SqlRequestError, A])): Column[A]` to `def nonNull[A](transformer: ((Any, MetaDataItem) => Either[SqlRequestError, A])): Column[A] = Column[A]`.

The deprecated operations `.list()`, `.single()` and `.singleOpt()` on SQL result (e.g. `SQL("...").list()`) are now removed, and must be respectively replaced by `.as(parser.*)`, `.as(parser.single)` and `.as(parser.singleOpt)`.

The `.getFilledStatement` has been removed.

The former streaming operation `.apply()` is now removed, and must be replaced by either `.fold`, `.foldWhile` or `.withResult`.

## Iteratees module

A new Anorm module is available to ease the integration with [Play Iteratees](https://www.playframework.com/documentation/latest/Iteratees).

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

## Column aliaser

Consider the following query result.

```
=> SELECT * FROM test t1 JOIN (SELECT * FROM test WHERE parent_id ISNULL) t2 ON t1.parent_id=t2.id WHERE t1.id='bar';
 id  | value  | parent_id | id  | value  | parent_id 
-----+--------+-----------+-----+--------+-----------
 bar | value2 | foo       | foo | value1 | 
(1 row)
```

The table aliases `t1` and `t2` are not supported in JDBC, so Anorm introduces the `ColumnAliaser` to be able to define user aliases over columns as following.

```scala
import anorm._

val parser: RowParser[(String, String, String, Option[String])] = SqlParser.str("id") ~ SqlParser.str("value") ~ SqlParser.str("parent.value") ~ SqlParser.str("parent.parent_id").? map(SqlParser.flatten)

val aliaser: ColumnAliaser = ColumnAliaser.withPattern((3 to 6).toSet, "parent.")

val res: Try[(String, String, String, Option[String])] = SQL"""SELECT * FROM test t1 JOIN (SELECT * FROM test WHERE parent_id ISNULL) t2 ON t1.parent_id=t2.id WHERE t1.id=${"bar"}""".asTry(parser.single, aliaser)

res.foreach {
  case (id, value, parentVal, grandPaId) => ???
}
```

## Column conversions

- A date/timestamp column can now be read as a `Long`, representing the [epoch](https://en.wikipedia.org/wiki/Unix_time) milliseconds.

## Macros

The `offsetParser[T]` macro is added.

```scala
case class Foo(name: String, age: Int)

import anorm._

val findAll = SQL"SELECT uninteresting_col, skip_col, name, age FROM foo"

val fooParser = Macro.offsetParser[Foo](2)
// ignore uninteresting_col & skip_col
```

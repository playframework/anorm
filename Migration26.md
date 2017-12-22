# Anorm 2.6 Migration Guide

This is a guide for migrating from Anorm 2.5 to Anorm 2.6. If you need to migrate from an earlier version of Anorm then you must first follow the [Anorm 2.5 Migration Guide](https://github.com/playframework/anorm/blob/master/Migration25.md#anorm-25-migration-guide).

**Note:** The dependency group has been updated from `com.typesafe.play` to `org.playframework.anorm`.

## Streaming

The streaming support has been improved for a reactive processing of the results, with new modules.

### Akka Stream module

A new [Akka](http://doc.akka.io/docs/akka/2.4.12/scala/stream/index.html) module is available to process DB results as [Sources](doc.akka.io/api/akka/2.4.12/#akka.stream.javadsl.Source).

To do so, the Anorm Akka module must be used.

```scala
libraryDependencies ++= Seq(
  "org.playframework.anorm" %% "anorm-akka" % "ANORM_VERSION",
  "com.typesafe.akka" %% "akka-stream" % "2.4.12")
```

> This module is tested with Akka Stream 2.4.12.

Once this library is available, the query can be used as streaming source.

```scala
import java.sql.Connection

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source

import anorm._

def resultSource(implicit m: Materializer, con: Connection): Source[String, NotUsed] = AkkaStream.source(SQL"SELECT * FROM Test", SqlParser.scalar[String], ColumnAliaser.empty)
```

### Iteratees module

A new Anorm module is available to ease the integration with [Play Iteratees](https://www.playframework.com/documentation/latest/Iteratees).

It can be added to your project using the following dependencies.

```
libraryDependencies ++= Seq(
  "org.playframework.anorm" %% "anorm-iteratee" % "ANORM_VERSION",
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

There are also new variant of the `namedParser` macros, which can be passed a naming strategy. This naming strategy determines the column name for each case class property; e.g. To use snake case:

```scala
import anorm.{ Macro, RowParser }, Macro.ColumnNaming

case class Info(name: String, lastModified: Long)

val parser: RowParser[Info] = Macro.namedParser[Info](ColumnNaming.SnakeCase)
/* Generated as:
get[String]("name") ~ get[Long]("last_modified") map {
  case name ~ year => Info(name, year)
}
*/
```

A custom column naming can be defined using `ColumnNaming(String => String)`.

The macros can now use already defined `RowParser` as sub-parser.

```scala
case class Bar(lorem: Float, ipsum: Long)
case class Foo(name: String, bar: Bar, age: Int)

import anorm._

// nested parser
implicit val barParser = Macro.parser[Bar]("bar_lorem", "bar_ipsum")

val fooBar = Macro.namedParser[Foo] /* generated as:
  get[String]("name") ~ barParser ~ get[Int]("age") map {
    case name ~ bar ~ age => Foo(name, bar, age)
  }
*/

val result: Foo = SQL"""SELECT f.name, age, bar_lorem, bar_ipsum 
  FROM foo f JOIN bar b ON f.name=b.name WHERE f.name=${"Foo"}""".
  as(fooBar.single)
```

## ToParameterList

The new typeclass `ToParameterList` has been introduced to define more complete encoders for parameters, for example encoder for a case case.

It comes with useful macros to easily generate such parameter conversions.

```scala
import anorm.{ Macro, SQL, ToParameterList }
import anorm.NamedParameter, NamedParameter.{ namedWithString => named }

case class Bar(v: Int)
case class Foo(n: Int, bar: Bar)

// Convert all supported properties as parameters 
implicit val barToParams: ToParameterList[Bar] = Macro.toParameters()

// Custom-manual statement using-knowing class properties order
SQL("INSERT INTO table(col_w) VALUES({v})").
  bind(Bar(1)) // bind as param using implicit barToParams
```

## PostgreSQL

A new module dedicated to PostgreSQL provides parameter and column conversions improved for this database, for JSON values and UUID.

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

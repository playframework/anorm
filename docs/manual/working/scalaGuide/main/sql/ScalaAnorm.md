<!--- Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com> -->
# Anorm, simple SQL data access

Play includes a simple data access layer called Anorm that uses plain SQL to interact with the database and provides an API to parse and transform the resulting datasets.

**Anorm is Not an Object Relational Mapper**

> In the following documentation, we will use the [MySQL world sample database](http://dev.mysql.com/doc/index-other.html). 
> 
> If you want to enable it for your application, follow the MySQL website instructions, and configure it as explained [[on the Scala database page | ScalaDatabase]].

## Overview

It can feel strange to return to plain old SQL to access an SQL database these days, especially for Java developers accustomed to using a high-level Object Relational Mapper like Hibernate to completely hide this aspect.

Although we agree that these tools are almost required in Java, we think that they are not needed at all when you have the power of a higher-level programming language like Scala. On the contrary, they will quickly become counter-productive.

#### Using JDBC is a pain, but we provide a better API

We agree that using the JDBC API directly is tedious, particularly in Java. You have to deal with checked exceptions everywhere and iterate over and over around the ResultSet to transform this raw dataset into your own data structure.

We provide a simpler API for JDBC; using Scala you don’t need to bother with exceptions, and transforming data is really easy with a functional language. In fact, the goal of the Play Scala SQL access layer is to provide several APIs to effectively transform JDBC data into other Scala structures.

#### You don’t need another DSL to access relational databases

SQL is already the best DSL for accessing relational databases. We don’t need to invent something new. Moreover the SQL syntax and features can differ from one database vendor to another. 

If you try to abstract this point with another proprietary SQL like DSL you will have to deal with several ‘dialects’ dedicated for each vendor (like Hibernate ones), and limit yourself by not using a particular database’s interesting features.

Play will sometimes provide you with pre-filled SQL statements, but the idea is not to hide the fact that we use SQL under the hood. Play just saves typing a bunch of characters for trivial queries, and you can always fall back to plain old SQL.

#### A type safe DSL to generate SQL is a mistake

Some argue that a type safe DSL is better since all your queries are checked by the compiler. Unfortunately the compiler checks your queries based on a meta-model definition that you often write yourself by ‘mapping’ your data structure to the database schema. 

There are no guarantees that this meta-model is correct. Even if the compiler says that your code and your queries are correctly typed, it can still miserably fail at runtime because of a mismatch in your actual database definition.

#### Take Control of your SQL code

Object Relational Mapping works well for trivial cases, but when you have to deal with complex schemas or existing databases, you will spend most of your time fighting with your ORM to make it generate the SQL queries you want.

Writing SQL queries yourself can be tedious for a simple ‘Hello World’ application, but for any real-life application, you will eventually save time and simplify your code by taking full control of your SQL code.

## Add Anorm to your project

You will need to add Anorm and JDBC plugin to your dependencies : 

```scala
libraryDependencies ++= Seq(
  jdbc,
  "com.typesafe.play" %% "anorm" % "2.5.1"
)
```

## Executing SQL queries

To start you need to learn how to execute SQL queries.

First, import `anorm._`, and then simply use the `SQL` object to create queries. You need a `Connection` to run a query, and you can retrieve one from the `play.api.db.DB` helper:

@[playdb](code/ScalaAnorm.scala)

The `execute()` method returns a Boolean value indicating whether the execution was successful.

To execute an update, you can use `executeUpdate()`, which returns the number of rows updated.

```scala
val result: Int = SQL("delete from City where id = 99").executeUpdate()
```

If you are inserting data that has an auto-generated `Long` primary key, you can call `executeInsert()`.

```scala
val id: Option[Long] = 
  SQL("insert into City(name, country) values ({name}, {country})")
  .on('name -> "Cambridge", 'country -> "New Zealand").executeInsert()
```

When key generated on insertion is not a single `Long`, `executeInsert` can be passed a `ResultSetParser` to return the correct key.

```scala
import anorm.SqlParser.str

val id: List[String] = 
  SQL("insert into City(name, country) values ({name}, {country})")
  .on('name -> "Cambridge", 'country -> "New Zealand")
  .executeInsert(str.+) // insertion returns a list of at least one string keys
```

Since Scala supports multi-line strings, feel free to use them for complex SQL statements:

```scala
val sqlQuery = SQL(
  """
    select * from Country c 
    join CountryLanguage l on l.CountryCode = c.Code 
    where c.code = 'FRA';
  """
)
```

If your SQL query needs dynamic parameters, you can declare placeholders like `{name}` in the query string, and later assign a value to them:

```scala
SQL(
  """
    select * from Country c 
    join CountryLanguage l on l.CountryCode = c.Code 
    where c.code = {countryCode};
  """
).on("countryCode" -> "FRA")
```

You can also use string interpolation to pass parameters (see details thereafter).

In case several columns are found with same name in query result, for example columns named `code` in both `Country` and `CountryLanguage` tables, there can be ambiguity. By default a mapping like following one will use the last column:

```scala
import anorm.{ SQL, SqlParser }

val code: String = SQL(
  """
    select * from Country c 
    join CountryLanguage l on l.CountryCode = c.Code 
    where c.code = {countryCode}
  """)
  .on("countryCode" -> "FRA").as(SqlParser.str("code").single)
```

If `Country.Code` is 'First' and `CountryLanguage` is 'Second', then in previous example `code` value will be 'Second'. Ambiguity can be resolved using qualified column name, with table name:

```scala
import anorm.{ SQL, SqlParser }

val code: String = SQL(
  """
    select * from Country c 
    join CountryLanguage l on l.CountryCode = c.Code 
    where c.code = {countryCode}
  """)
  .on("countryCode" -> "FRA").as(SqlParser.str("Country.code").single)
// code == "First"
```

When a column is aliased, typically using SQL `AS`, its value can also be resolved. Following example parses column with `country_lang` alias.

```scala
import anorm.{ SQL, SqlParser }

val lang: String = SQL(
  """
    select l.language AS country_lang from Country c 
    join CountryLanguage l on l.CountryCode = c.Code 
    where c.code = {countryCode}
  """).on("countryCode" -> "FRA").
    as(SqlParser.str("country_lang").single)
```

Columns can also be specified by position, rather than name:

```scala
import anorm.SqlParser.{ str, float }
// Parsing column by name or position
val parser = 
  str("name") ~ float(3) /* third column as float */ map {
    case name ~ f => (name -> f)
  }

val product: (String, Float) = SQL("SELECT * FROM prod WHERE id = {id}").
  on('id -> "p").as(parser.single)
```

If the columns are not strictly defined (e.g. with types that can vary), the `SqlParser.folder` can be used to fold each row in a custom way.

```scala
import anorm.{ RowParser, SqlParser }

val parser: RowParser[Map[String, Any]] = 
  SqlParser.folder(Map.empty[String, Any]) { (map, value, meta) => 
    Right(map + (meta.column.qualified -> value))
  }

val result: List[Map[String, Any]] = SQL"SELECT * FROM dyn_table".as(parser.*)
```

If the columns are not strictly defined (e.g. with types that can vary), the `SqlParser.folder` can be used to fold each row in a custom way.

```scala
import anorm.{ RowParser, SqlParser }

val parser: RowParser[Map[String, Any]] = 
  SqlParser.folder(Map.empty[String, Any]) { (map, value, meta) => 
    Right(map + (meta.column.qualified -> value))
  }

val result: List[Map[String, Any]] = SQL"SELECT * FROM dyn_table".as(parser.*)
```

#### Table alias

With some databases, it's possible to define aliases for table (or for sub-query), as in the following example.

```
=> SELECT * FROM test t1 JOIN (SELECT * FROM test WHERE parent_id ISNULL) t2 ON t1.parent_id=t2.id WHERE t1.id='bar';
 id  | value  | parent_id | id  | value  | parent_id 
-----+--------+-----------+-----+--------+-----------
 bar | value2 | foo       | foo | value1 | 
(1 row)
```

Unfortunately, such aliases are not supported in JDBC, so Anorm introduces the `ColumnAliaser` to be able to define user aliases over columns.

```scala
import anorm._

val parser: RowParser[(String, String, String, Option[String])] = SqlParser.str("id") ~ SqlParser.str("value") ~ SqlParser.str("parent.value") ~ SqlParser.str("parent.parent_id").? map(SqlParser.flatten)

val aliaser: ColumnAliaser = ColumnAliaser.withPattern((3 to 6).toSet, "parent.")

val res: Try[(String, String, String, Option[String])] = SQL"""SELECT * FROM test t1 JOIN (SELECT * FROM test WHERE parent_id ISNULL) t2 ON t1.parent_id=t2.id WHERE t1.id=${"bar"}""".asTry(parser.single, aliaser)

res.foreach {
  case (id, value, parentVal, grandPaId) => ???
}
```

### SQL queries using String Interpolation

Since Scala 2.10 supports custom String Interpolation there is also a 1-step alternative to `SQL(queryString).on(params)` seen before. You can abbreviate the code as: 

```scala
val name = "Cambridge"
val country = "New Zealand"

SQL"insert into City(name, country) values ($name, $country)"
```

It also supports multi-line string and inline expresions:

```scala
val lang = "French"
val population = 10000000
val margin = 500000

val code: String = SQL"""
  select * from Country c 
    join CountryLanguage l on l.CountryCode = c.Code 
    where l.Language = $lang and c.Population >= ${population - margin}
    order by c.Population desc limit 1"""
  .as(SqlParser.str("Country.code").single)
```

This feature tries to make faster, more concise and easier to read the way to retrieve data in Anorm. Please, feel free to use it wherever you see a combination of `SQL().on()` functions (or even an only `SQL()` without parameters).

By using `#$value` instead of `$value`, interpolated value will be part of the prepared statement, rather being passed as a parameter when executing this SQL statement (e.g. `#$cmd` and `#$table` in example bellow).

```scala
val cmd = "SELECT"
val table = "Test"

SQL"""#$cmd * FROM #$table WHERE id = ${"id1"} AND code IN (${Seq(2, 5)})"""

// prepare the SQL statement, with 1 string and 2 integer parameters:
// SELECT * FROM Test WHERE id = ? AND code IN (?, ?)
```

## Generated parsers

The macro `namedParser[T]` can be used to create a `RowParser[T]` at compile-time, for any case class `T`.

```scala
import anorm.{ Macro, RowParser }

case class Info(name: String, year: Option[Int])

val parser: RowParser[Info] = Macro.namedParser[Info]
/* Generated as:
get[String]("name") ~ get[Option[Int]]("year") map {
  case name ~ year => Info(name, year)
}
*/

val result: List[Info] = SQL"SELECT * FROM list".as(parser.*)
```

The similar macros `indexedParser[T]` and `offsetParser[T]` are available to get column values by positions instead of names.

```scala
import anorm.{ Macro, RowParser }

case class Info(name: String, year: Option[Int])

val parser1: RowParser[Info] = Macro.indexedParser[Info]
/* Generated as:
get[String](1) ~ get[Option[Int]](2) map {
  case name ~ year => Info(name, year)
}
*/

val result1: List[Info] = SQL"SELECT * FROM list".as(parser1.*)

// With offset
val parser2: RowParser[Info] = Macro.offsetParser[Info](2)
/* Generated as:
get[String](2 + 1) ~ get[Option[Int]](2 + 2) map {
  case name ~ year => Info(name, year)
}
*/

val result2: List[Info] = SQL"SELECT * FROM list".as(parser2.*)
```

To indicate custom names for the columns to be parsed, the macro `parser[T](names)` can be used.

```scala
import anorm.{ Macro, RowParser }

case class Info(name: String, year: Option[Int])

val parser: RowParser[Info] = Macro.parser[Info]("a_name", "creation")
/* Generated as:
get[String]("a_name") ~ get[Option[Int]]("creation") map {
  case name ~ year => Info(name, year)
}
*/

val result: List[Info] = SQL"SELECT * FROM list".as(parser.*)
```

The `RowParser` exposed in the implicit scope can be used as nested one generated by the macros.

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

> The `anorm.macro.debug` system property can be set to `true` (e.g. `sbt -Danorm.macro.debug=true ...`) to debug the generated parsers.

## Streaming results

Query results can be processed row per row, not having all loaded in memory.

In the following example we will count the number of country rows.

```scala
val countryCount: Either[List[Throwable], Long] = 
  SQL"Select count(*) as c from Country".fold(0L) { (c, _) => c + 1 }
```

> In previous example, either it's the successful `Long` result (right), or the list of errors (left).

Result can also be partially processed:

```scala
val books: Either[List[Throwable], List[String]] = 
  SQL("Select name from Books").foldWhile(List[String]()) { (list, row) => 
    if (list.size == 100) (list -> false) // stop with `list`
    else (list := row[String]("name")) -> true // continue with one more name
  }
```

It's possible to use a custom streaming:

```scala
import anorm.{ Cursor, Row }

@annotation.tailrec
def go(c: Option[Cursor], l: List[String]): List[String] = c match {
  case Some(cursor) => {
    if (l.size == 100) l // custom limit, partial processing
    else {
      go(cursor.next, l :+ cursor.row[String]("name"))
    }
  }
  case _ => l
}

val books: Either[List[Throwable], List[String]] = 
  SQL("Select name from Books").withResult(go(_, List.empty[String]))
```

The parsing API can be used with streaming, using `RowParser` on each cursor `.row`. The previous example can be updated with row parser.

```scala
import scala.util.{ Try, Success => TrySuccess, Failure }

// bookParser: anorm.RowParser[Book]

@annotation.tailrec
def go(c: Option[Cursor], l: List[Book]): Try[List[Book]] = c match {
  case Some(cursor) => {
    if (l.size == 100) l // custom limit, partial processing
    else {
      val parsed: Try[Book] = cursor.row.as(bookParser)

      parsed match {
        case TrySuccess(book) => // book successfully parsed from row
          go(cursor.next, l :+ book)
        case Failure(f) => /* fails to parse a book */ Failure(f)
      }
    }
  }
  case _ => l
}

val books: Either[List[Throwable], Try[List[Book]]] = 
  SQL("Select name from Books").withResult(go(_, List.empty[Book]))

books match {
  case Left(streamingErrors) => ???
  case Right(Failure(parsingError)) => ???
  case Right(TrySuccess(listOfBooks)) => ???
}
```

### Akka Stream

The query result from Anorm can be processed as [Source](doc.akka.io/api/akka/2.4.4/#akka.stream.javadsl.Source) with [Akka Stream](http://doc.akka.io/docs/akka/2.4.4/scala/stream/index.html).

To do so, the Anorm Akka module must be used.

```scala
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "anorm-akka" % "ANORM_VERSION",
  "com.typesafe.akka" %% "akka-stream" % "2.4.4")
```

> This module is tested with Akka Stream 2.4.4.

Once this library is available, the query can be used as streaming source.

```scala
import java.sql.Connection

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }

import anorm._

def resultSource(implicit m: Materializer, con: Connection): Source[String, NotUsed] = AkkaStream.source(SQL"SELECT * FROM Test", SqlParser.scalar[String], ColumnAliaser.empty)

def countStrings()(implicit m: Materializer, con: Connection): Future[Int] =
  resultSource.runWith(
    Sink.fold[Int, String](0) { (count, str) => count + str.length })
```

### Iteratee

It's possible to use Anorm along with [Play Iteratees](https://www.playframework.com/documentation/latest/Iteratees), using the following dependencies.

```scala
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

## Multi-value support

Anorm parameter can be multi-value, like a sequence of string.
In such case, values will be prepared to be passed to JDBC.

```scala
// With default formatting (", " as separator)
SQL("SELECT * FROM Test WHERE cat IN ({categories})").
  on('categories -> Seq("a", "b", "c")
// -> SELECT * FROM Test WHERE cat IN ('a', 'b', 'c')

// With custom formatting
import anorm.SeqParameter
SQL("SELECT * FROM Test t WHERE {categories}").
  on('categories -> SeqParameter(
    values = Seq("a", "b", "c"), separator = " OR ", 
    pre = "EXISTS (SELECT NULL FROM j WHERE t.id=j.id AND name=",
    post = ")"))
/* ->
SELECT * FROM Test t WHERE 
EXISTS (SELECT NULL FROM j WHERE t.id=j.id AND name='a') 
OR EXISTS (SELECT NULL FROM j WHERE t.id=j.id AND name='b') 
OR EXISTS (SELECT NULL FROM j WHERE t.id=j.id AND name='c')
*/
```

On purpose multi-value parameter must strictly be declared with one of supported types (`List`, 'Seq`, `Set`, `SortedSet`, `Stream`, `Vector`  and `SeqParameter`). Value of a subtype must be passed as parameter with supported:

```scala
val seq = IndexedSeq("a", "b", "c")
// seq is instance of Seq with inferred type IndexedSeq[String]

// Wrong
SQL"SELECT * FROM Test WHERE cat in ($seq)"
// Erroneous - No parameter conversion for IndexedSeq[T]

// Right
SQL"SELECT * FROM Test WHERE cat in (${seq: Seq[String]})"

// Right
val param: Seq[String] = seq
SQL"SELECT * FROM Test WHERE cat in ($param)"
```

In case parameter type is JDBC array (`java.sql.Array`), its value can be passed as `Array[T]`, as long as element type `T` is a supported one.

```scala
val arr = Array("fr", "en", "ja")
SQL"UPDATE Test SET langs = $arr".execute()
```

A column can also be multi-value if its type is JDBC array (`java.sql.Array`), then it can be mapped to either array or list (`Array[T]` or `List[T]`), provided type of element (`T`) is also supported in column mapping.

```scala
import anorm.SQL
import anorm.SqlParser.{ scalar, * }

// array and element parser
import anorm.Column.{ columnToArray, stringToArray }

val res: List[Array[String]] =
  SQL("SELECT str_arr FROM tbl").as(scalar[Array[String]].*)
```

> Convenient parsing functions is also provided for arrays with `SqlParser.array[T](...)` and `SqlParser.list[T](...)`.

### Batch update

When you need to execute SQL statement several times with different arguments, batch query can be used (e.g. to execute a batch of insertions).

```scala
import anorm.BatchSql

val batch = BatchSql(
  "INSERT INTO books(title, author) VALUES({title}, {author})", 
  Seq[NamedParameter]("title" -> "Play 2 for Scala", 
    "author" -> "Peter Hilton"),
  Seq[NamedParameter]("title" -> "Learning Play! Framework 2", 
    "author" -> "Andy Petrella"))

val res: Array[Int] = batch.execute() // array of update count
```

> Batch update must be called with at least one list of parameter. If a batch is executed with the mandatory first list of parameter being empty (e.g. `Nil`), only one statement will be executed (without parameter), which is equivalent to `SQL(statement).executeUpdate()`.

### Edge cases

Type of parameter value should be visible, to be properly set on SQL statement.
Using value as `Any`, explicitly or due to erasure, leads to compilation error `No implicit view available from Any => anorm.ParameterValue`.

```scala
// Wrong #1
val p: Any = "strAsAny"
SQL("SELECT * FROM test WHERE id={id}").
  on('id -> p) // Erroneous - No conversion Any => ParameterValue

// Right #1
val p = "strAsString"
SQL("SELECT * FROM test WHERE id={id}").on('id -> p)

// Wrong #2
val ps = Seq("a", "b", 3) // inferred as Seq[Any]
SQL("SELECT * FROM test WHERE (a={a} AND b={b}) OR c={c}").
  on('a -> ps(0), // ps(0) - No conversion Any => ParameterValue
    'b -> ps(1), 
    'c -> ps(2))

// Right #2
val ps = Seq[anorm.ParameterValue]("a", "b", 3) // Seq[ParameterValue]
SQL("SELECT * FROM test WHERE (a={a} AND b={b}) OR c={c}").
  on('a -> ps(0), 'b -> ps(1), 'c -> ps(2))

// Wrong #3
val ts = Seq( // Seq[(String -> Any)] due to _2
  "a" -> "1", "b" -> "2", "c" -> 3)

val nps: Seq[NamedParameter] = ts map { t => 
  val p: NamedParameter = t; p
  // Erroneous - no conversion (String,Any) => NamedParameter
}

SQL("SELECT * FROM test WHERE (a={a} AND b={b}) OR c={c}").on(nps :_*) 

// Right #3
val nps = Seq[NamedParameter]( // Tuples as NamedParameter before Any
  "a" -> "1", "b" -> "2", "c" -> 3)
SQL("SELECT * FROM test WHERE (a={a} AND b={b}) OR c={c}").
  on(nps: _*) // Fail - no conversion (String,Any) => NamedParameter
```

In some cases, some JDBC drivers returns a result set positioned on the first row rather than [before this first row](http://docs.oracle.com/javase/7/docs/api/java/sql/ResultSet.html) (e.g. stored procedured with Oracle JDBC driver).
To handle such edge-case, `.withResultSetOnFirstRow(true)` can be used as following.

```scala
SQL("EXEC stored_proc {arg}").on("arg" -> "val").withResultSetOnFirstRow(true)
SQL"""EXEC stored_proc ${"val"}""".withResultSetOnFirstRow(true)

SQL"INSERT INTO dict(term, definition) VALUES ($term, $definition)".
  withResultSetOnFirstRow(true).executeInsert()
// Also needed on executeInsert for such driver, 
// as a ResultSet is returned in this case for the generated keys
```

## Using Pattern Matching

You can also use Pattern Matching to match and extract the `Row` content. In this case the column name doesn’t matter. Only the order and the type of the parameters is used to match.

The following example transforms each row to the correct Scala type:

```scala
import java.sql.Connection
import anorm._

trait Country
case class SmallCountry(name:String) extends Country
case class BigCountry(name:String) extends Country
case object France extends Country

val patternParser = RowParser[Country] {
  case Row("France", _) => Success(France)
  case Row(name:String, pop:Int) if (pop > 1000000) => Success(BigCountry(name))
  case Row(name:String, _) => Success(SmallCountry(name))
  case row => Error(TypeDoesNotMatch(s"unexpected: $row"))
}

def countries(implicit con: Connection): List[Country] =
  SQL("SELECT name,population FROM Country WHERE id = {i}").
    on("i" -> "id").as(patternParser.*)
```

## Using for-comprehension

Row parser can be defined as for-comprehension, working with SQL result type. It can be useful when working with lot of column, possibly to work around case class limit.

```scala
import anorm.SqlParser.{ str, int }

val parser = for {
  a <- str("colA")
  b <- int("colB")
} yield (a -> b)

val parsed: (String, Int) = SELECT("SELECT * FROM Test").as(parser.single)
```

## Retrieving data along with execution context

Moreover data, query execution involves context information like SQL warnings that may be raised (and may be fatal or not), especially when working with stored SQL procedure.

Way to get context information along with query data is to use `executeQuery()`:

```scala
import anorm.SqlQueryResult

val res: SqlQueryResult = SQL("EXEC stored_proc {code}").
  on('code -> code).executeQuery()

// Check execution context (there warnings) before going on
val str: Option[String] =
  res.statementWarning match {
    case Some(warning) =>
      warning.printStackTrace()
      None

    case _ => res.as(scalar[String].singleOpt) // go on row parsing
  }
```

## Working with optional/nullable values

If a column in database can contain `Null` values, you need to parse it as an `Option` type.

For example, the `indepYear` of the `Country` table is nullable, so you need to match it as `Option[Int]`:

```scala
case class Info(name: String, year: Option[Int])

val parser = str("name") ~ get[Option[Int]]("indepYear") map {
  case n ~ y => Info(n, y)
}

val res: List[Info] = SQL("Select name,indepYear from Country").as(parser.*)
```

If you try to match this column as `Int` it won’t be able to parse `Null` values. Suppose you try to retrieve the column content as `Int` directly from the dictionary:

```scala
SQL("Select name,indepYear from Country")().map { row =>
  row[String]("name") -> row[Int]("indepYear")
}
```

This will produce an `UnexpectedNullableFound(COUNTRY.INDEPYEAR)` exception if it encounters a null value, so you need to map it properly to an `Option[Int]`.

A nullable parameter is also passed as `Option[T]`, `T` being parameter base type (see *Parameters* section thereafter).

> Passing directly `None` for a NULL value is not supported, as inferred as `Option[Nothing]` (`Nothing` being unsafe for a parameter value). In this case, `Option.empty[T]` must be used.

```scala
// OK: 

SQL("INSERT INTO Test(title) VALUES({title})").on("title" -> Some("Title"))

val title1 = Some("Title1")
SQL("INSERT INTO Test(title) VALUES({title})").on("title" -> title1)

val title2: Option[String] = None
// None inferred as Option[String] on assignment
SQL("INSERT INTO Test(title) VALUES({title})").on("title" -> title2)

// Not OK:
SQL("INSERT INTO Test(title) VALUES({title})").on("title" -> None)

// OK:
SQL"INSERT INTO Test(title) VALUES(${Option.empty[String]})"
```

## Using the Parser API

You can use the parser API to create generic and reusable parsers that can parse the result of any select query.

> **Note:** This is really useful, since most queries in a web application will return similar data sets. For example, if you have defined a parser able to parse a `Country` from a result set, and another `Language` parser, you can then easily compose them to parse both Country and Language from a join query.
>
> First you need to `import anorm.SqlParser._`

### Getting a single result

First you need a `RowParser`, i.e. a parser able to parse one row to a Scala value. For example we can define a parser to transform a single column result set row, to a Scala `Long`:

```scala
val rowParser = scalar[Long]
```

Then we have to transform it into a `ResultSetParser`. Here we will create a parser that parse a single row:

```scala
val rsParser = scalar[Long].single
```

So this parser will parse a result set to return a `Long`. It is useful to parse to result produced by a simple SQL `select count` query:

```scala
val count: Long = 
  SQL("select count(*) from Country").as(scalar[Long].single)
```

If expected single result is optional (0 or 1 row), then `scalar` parser can be combined with `singleOpt`:

```scala
val name: Option[String] =
  SQL"SELECT name FROM Country WHERE code = $code" as scalar[String].singleOpt
```

### Getting a single optional result

Let's say you want to retrieve the country_id from the country name, but the query might return null. We'll use the singleOpt parser :

```scala
val countryId: Option[Long] = 
  SQL("SELECT country_id FROM Country C WHERE C.country='France'")
  .as(scalar[Long].singleOpt)
```

### Getting a more complex result

Let’s write a more complicated parser:

`str("name") ~ int("population")`, will create a `RowParser` able to parse a row containing a String `name` column and an Integer `population` column. Then we can create a `ResultSetParser` that will parse as many rows of this kind as it can, using `*`: 

```scala
val populations: List[String ~ Int] = 
  SQL("SELECT * FROM Country").as((str("name") ~ int("population")).*) 
```

As you see, this query’s result type is `List[String ~ Int]` - a list of country name and population items.

You can also rewrite the same code as:

```scala
val result: List[String ~ Int] = SQL("SELECT * FROM Country").
  as((get[String]("name") ~ get[Int]("population")).*)

```

Now what about the `String~Int` type? This is an **Anorm** type that is not really convenient to use outside of your database access code. You would rather have a simple tuple `(String, Int)` instead. You can use the `map` function on a `RowParser` to transform its result to a more convenient type:

```scala
val parser = str("name") ~ int("population") map { case n ~ p => (n, p) }
```

> **Note:** We created a tuple `(String, Int)` here, but there is nothing stopping you from transforming the `RowParser` result to any other type, such as a custom case class.

Now, because transforming `A ~ B ~ C` types to `(A, B, C)` is a common task, we provide a `flatten` function that does exactly that. So you finally write:

```scala
val result: List[(String, Int)] = 
  SQL("select * from Country").as(parser.flatten.*)
```

A `RowParser` can be combined with any function to applied it with extracted columns.

```scala
import anorm.SqlParser.{ int, str, to }

def display(name: String, population: Int): String = 
  s"The population in $name is of $population."

val parser = str("name") ~ int("population") map (to(display _))
```

> **Note:** The mapping function must be partially applied (syntax `fn _`) when given `to` the parser (see [SLS 6.26.2, 6.26.5 - Eta expansion](http://www.scala-lang.org/docu/files/ScalaReference.pdf)).

If list should not be empty, `parser.+` can be used instead of `parser.*`.

Anorm is providing parser combinators other than the most common `~` one: `~>`, `<~`.

```scala
import anorm.{ SQL, SqlParser }, SqlParser.{ int, str }

// Combinator ~>
val String = SQL("SELECT * FROM test").as((int("id") ~> str("val")).single)
  // row has to have an int column 'id' and a string 'val' one,
  // keeping only 'val' in result

val Int = SQL("SELECT * FROM test").as((int("id") <~ str("val")).single)
  // row has to have an int column 'id' and a string 'val' one,
  // keeping only 'id' in result
```

### A more complicated example

Now let’s try with a more complicated example. How to parse the result of the following query to retrieve the country name and all spoken languages for a country code?

```sql
select c.name, l.language from Country c 
    join CountryLanguage l on l.CountryCode = c.Code 
    where c.code = 'FRA'
```

Let’s start by parsing all rows as a `List[(String,String)]` (a list of name,language tuple):

```scala
var p: ResultSetParser[List[(String,String)]] = {
  str("name") ~ str("language") map(flatten) *
}
```

Now we get this kind of result:

```scala
List(
  ("France", "Arabic"), 
  ("France", "French"), 
  ("France", "Italian"), 
  ("France", "Portuguese"), 
  ("France", "Spanish"), 
  ("France", "Turkish")
)
```

We can then use the Scala collection API, to transform it to the expected result:

```scala
case class SpokenLanguages(country:String, languages:Seq[String])

languages.headOption.map { f =>
  SpokenLanguages(f._1, languages.map(_._2))
}
```

Finally, we get this convenient function:

```scala
case class SpokenLanguages(country:String, languages:Seq[String])

def spokenLanguages(countryCode: String): Option[SpokenLanguages] = {
  val languages: List[(String, String)] = SQL(
    """
      select c.name, l.language from Country c 
      join CountryLanguage l on l.CountryCode = c.Code 
      where c.code = {code};
    """
  )
  .on("code" -> countryCode)
  .as(str("name") ~ str("language") map(flatten) *)

  languages.headOption.map { f =>
    SpokenLanguages(f._1, languages.map(_._2))
  }
}
```

To continue, let’s complicate our example to separate the official language from the others:

```scala
case class SpokenLanguages(
  country:String, 
  officialLanguage: Option[String], 
  otherLanguages:Seq[String]
)

def spokenLanguages(countryCode: String): Option[SpokenLanguages] = {
  val languages: List[(String, String, Boolean)] = SQL(
    """
      select * from Country c 
      join CountryLanguage l on l.CountryCode = c.Code 
      where c.code = {code};
    """
  )
  .on("code" -> countryCode)
  .as {
    str("name") ~ str("language") ~ str("isOfficial") map {
      case n~l~"T" => (n,l,true)
      case n~l~"F" => (n,l,false)
    } *
  }

  languages.headOption.map { f =>
    SpokenLanguages(
      f._1, 
      languages.find(_._3).map(_._2),
      languages.filterNot(_._3).map(_._2)
    )
  }
}
```

If you try this on the MySQL world sample database, you will get:

```scala
$ spokenLanguages("FRA")
> Some(
    SpokenLanguages(France,Some(French),List(
        Arabic, Italian, Portuguese, Spanish, Turkish
    ))
)
```

## JDBC mappings

As already seen in this documentation, Anorm provides builtins converters between JDBC and JVM types.

### Column parsers

Following table describes which JDBC numeric types (getters on `java.sql.ResultSet`, first column) can be parsed to which Java/Scala types (e.g. integer column can be read as double value).

↓JDBC / JVM➞           | BigDecimal<sup>1</sup> | BigInteger<sup>2</sup> | Boolean | Byte | Double | Float | Int | Long | Short
---------------------- | ---------------------- | ---------------------- | ------- | ---- | ------ | ----- | --- | ---- | -----
BigDecimal<sup>1</sup> | Yes                    | Yes                    | No      | No   | Yes    | No    | Yes | Yes  | No
BigInteger<sup>2</sup> | Yes                    | Yes                    | No      | No   | Yes    | Yes   | Yes | Yes  | No
Boolean                | No                     | No                     | Yes     | Yes  | No     | No    | Yes | Yes  | Yes
Byte                   | Yes                    | Yes                    | No      | Yes  | Yes    | Yes   | Yes | Yes  | Yes
Double                 | Yes                    | No                     | No      | No   | Yes    | No    | No  | No   | No
Float                  | Yes                    | No                     | No      | No   | Yes    | Yes   | No  | No   | No
Int                    | Yes                    | Yes                    | No      | No   | Yes    | Yes   | Yes | Yes  | No
Long                   | Yes                    | Yes                    | No      | No   | No     | No    | Yes | Yes  | No
Short                  | Yes                    | Yes                    | No      | Yes  | Yes    | Yes   | Yes | Yes  | Yes

- 1. Types `java.math.BigDecimal` and `scala.math.BigDecimal`.
- 2. Types `java.math.BigInteger` and `scala.math.BigInt`.

The second table shows mappings for the other supported types.

↓JDBC / JVM➞         | Array[T]<sup>3</sup> | Char | List<sup>3</sup> | String | UUID<sup>4</sup>
-------------------- | -------------------- | ---- | ---------------- | ------ | ----------------
Array<sup>5</sup>    | Yes                  | No   | Yes              | No     | No
Clob                 | No                   | Yes  | No               | Yes    | No
Iterable<sup>6</sup> | Yes                  | No   | Yes              | No     | No
Long                 | No                   | No   | No               | No     | No
String               | No                   | Yes  | No               | Yes    | Yes
UUID                 | No                   | No   | No               | No     | Yes

- 3. Array which type `T` of elements is supported.
- 4. Type `java.util.UUID`.
- 5. Type `java.sql.Array`.
- 6. Type `java.lang.Iterable[_]`.

> When an instance of `java.util.UUID` is used as a parameter, its string value is passed to statement. If the underlying database offers a specific SQL datatype for ID, it's possible to cast the stringified value; e.g. Working with PostgreSQL `uuid` type: `SQL"SELECT * FROM table WHERE anPostgreSQLuuid = ${aJavaUUID}::uuid"`

Optional column can be parsed as `Option[T]`, as soon as `T` is supported.

Binary data types are also supported.

↓JDBC / JVM➞            | Array[Byte] | InputStream<sup>1</sup>
----------------------- | ----------- | -----------------------
Array[Byte]             | Yes         | Yes
Blob<sup>2</sup>        | Yes         | Yes
Clob<sup>3</sup>        | No          | No
InputStream<sup>4</sup> | Yes         | Yes
Reader<sup>5</sup>      | No          | No

- 1. Type `java.io.InputStream`.
- 2. Type `java.sql.Blob`.
- 3. Type `java.sql.Clob`.
- 4. Type `java.io.Reader`.

CLOBs/TEXTs can be extracted as so:

```scala
SQL("Select name,summary from Country")().map {
  case Row(name: String, summary: java.sql.Clob) => name -> summary
}
```

Here we specifically chose to use `map`, as we want an exception if the row isn't in the format we expect.

Extracting binary data is similarly possible:

```scala
SQL("Select name,image from Country")().map {
  case Row(name: String, image: Array[Byte]) => name -> image
}
```

For types where column support is provided by Anorm, convenient functions are available to ease writing custom parsers. Each of these functions parses column either by name or index (> 1).

```scala
import anorm.SqlParser.str // String function

str("column")
str(1/* columnIndex)
```

Type                    | Function
------------------------|--------------
Array[Byte]             | byteArray
Boolean                 | bool
Byte                    | byte
Date                    | date
Double                  | double
Float                   | float
InputStream<sup>1</sup> | binaryStream
Int                     | int
Long                    | long
Short                   | short
String                  | str

- 1. Type `java.io.InputStream`.

The [Joda](http://www.joda.org) and [Java 8](#Java_8) temporal types are also supported.

↓JDBC / JVM➞                  | Date<sup>1</sup> | DateTime<sup>2</sup> | Instant<sup>3</sup> | Long
----------------------------- | ---------------- | -------------------- | -------------------
Date                          | Yes              | Yes                  | Yes                 | Yes
Long                          | Yes              | Yes                  | Yes                 | Yes
Timestamp                     | Yes              | Yes                  | Yes                 | Yes
Timestamp wrapper<sup>5</sup> | Yes              | Yes                  | Yes                 | Yes

- 1. Types `java.util.Date`, `org.joda.time.LocalDate` and `java.time.LocalDate`.
- 2. Types `org.joda.time.DateTime`, `org.joda.time.LocalDateTime`, `java.time.LocalDateTime` and `java.time.ZonedDateTime`.
- 3. Type `org.joda.time.Instant` and `java.time.Instant` (see Java 8).
- 5. Any type with a getter `getTimestamp` returning a `java.sql.Timestamp`.

It's possible to add custom mapping, for example if underlying DB doesn't support boolean datatype and returns integer instead. To do so, you have to provide a new implicit conversion for `Column[T]`, where `T` is the target Scala type:

```scala
import anorm.Column

// Custom conversion from JDBC column to Boolean
implicit def columnToBoolean: Column[Boolean] = 
  Column.nonNull1 { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case bool: Boolean => Right(bool) // Provided-default case
      case bit: Int      => Right(bit == 1) // Custom conversion
      case _             => Left(TypeDoesNotMatch(s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to Boolean for column $qualified"))
    }
  }
```

### Parameters

The following table indicates how JVM types are mapped to JDBC parameter types:

JVM                       | JDBC                                                  | Nullable
--------------------------|-------------------------------------------------------|----------
Array[T]<sup>1</sup>      | Array<sup>2</sup> with `T` mapping for each element   | Yes
BigDecimal<sup>3</sup>    | BigDecimal                                            | Yes
BigInteger<sup>4</sup>    | BigDecimal                                            | Yes
Boolean<sup>5</sup>       | Boolean                                               | Yes
Byte<sup>6</sup>          | Byte                                                  | Yes
Char<sup>7</sup>/String   | String                                                | Yes
Date/Timestamp            | Timestamp                                             | Yes
Double<sup>8</sup>        | Double                                                | Yes
Float<sup>9</sup>         | Float                                                 | Yes
Int<sup>10</sup>          | Int                                                   | Yes
List[T]                   | Multi-value<sup>11</sup>, with `T` mapping for each element | No
Long<sup>12</sup>         | Long                                                  | Yes
Object<sup>13</sup>       | Object                                                | Yes
Option[T]                 | `T` being type if some defined value                  | No
Seq[T]                    | Multi-value, with `T` mapping for each element        | No
Set[T]<sup>14</sup>       | Multi-value, with `T` mapping for each element        | No
Short<sup>15</sup>        | Short                                                 | Yes
SortedSet[T]<sup>16</sup> | Multi-value, with `T` mapping for each element        | No
Stream[T]                 | Multi-value, with `T` mapping for each element        | No
UUID                      | String<sup>17</sup>                                   | No
Vector                    | Multi-value, with `T` mapping for each element        | No

- 1. Type Scala `Array[T]`.
- 2. Type `java.sql.Array`.
- 3. Types `java.math.BigDecimal` and `scala.math.BigDecimal`.
- 4. Types `java.math.BigInteger` and `scala.math.BigInt`.
- 5. Types `Boolean` and `java.lang.Boolean`.
- 6. Types `Byte` and `java.lang.Byte`.
- 7. Types `Char` and `java.lang.Character`.
- 8. Types compatible with `java.util.Date`, and any wrapper type with `getTimestamp: java.sql.Timestamp`.
- 9. Types `Double` and `java.lang.Double`.
- 10. Types `Float` and `java.lang.Float`.
- 11. Types `Int` and `java.lang.Integer`.
- 12. Types `Long` and `java.lang.Long`.
- 13. Type `anorm.Object`, wrapping opaque object.
- 14. Multi-value parameter, with one JDBC placeholder (`?`) added for each element.
- 15. Type `scala.collection.immutable.Set`.
- 16. Types `Short` and `java.lang.Short`.
- 17. Type `scala.collection.immutable.SortedSet`.
- 18. Not-null value extracted using `.toString`.

> Passing `None` for a nullable parameter is deprecated, and typesafe `Option.empty[T]` must be use instead.

Large and stream parameters are also supported.

JVM                     | JDBC
------------------------|---------------
Array[Byte]             | Long varbinary
Blob<sup>1</sup>        | Blob
InputStream<sup>2</sup> | Long varbinary
Reader<sup>3</sup>      | Long varchar

- 1. Type `java.sql.Blob`
- 2. Type `java.io.InputStream`
- 3. Type `java.io.Reader`

[Joda](http://www.joda.org) and [Java 8](#Java_8) temporal types are supported as parameters:

JVM                       | JDBC
--------------------------|-----------
DateTime<sup>1</sup>      | Timestamp
Instant<sup>2</sup>       | Timestamp
LocalDate<sup>3</sup>     | Timestamp
LocalDateTime<sup>4</sup> | Timestamp
ZonedDateTime<sup>5</sup> | Timestamp

- 1. Type `org.joda.time.DateTime`.
- 2. Types `org.joda.time.Instant` and `java.time.Instant`.
- 3. Types `org.joda.time.LocalDate` and `java.time.LocalDate`.
- 4. Types `org.joda.time.LocalDateTime`, `org.joda.time.LocalDate` and `java.time.LocalDateTime`.
- 5. Type `java.time.ZonedDateTime`

To enable Joda types as parameter, the `import anorm.JodaParameterMetaData._` must be used.

#### Custom parameter conversions

Custom or database specific conversion for parameter can also be provided:

```scala
import java.sql.PreparedStatement
import anorm.{ ParameterMetaData, ToStatement }

// Custom conversion to statement for type T
implicit def customToStatement: ToStatement[T] = new ToStatement[T] {
  def set(statement: PreparedStatement, i: Int, value: T): Unit =
    ??? // Sets |value| on |statement|
}

// Metadata about the custom parameter type
implicit def customParamMeta: ParameterMetaData[T] = new ParameterMetaData[T] {
  val sqlType = "VARCHAR"
  def jdbcType = java.sql.Types.VARCHAR
}
```

If involved type accept `null` value, it must be appropriately handled in conversion. The `NotNullGuard` trait can be used to explicitly refuse `null` values in parameter conversion: `new ToStatement[T] with NotNullGuard { /* ... */ }`.

DB specific parameter can be explicitly passed as opaque value.
In this case at your own risk, `setObject` will be used on statement.

```scala
val anyVal: Any = myVal
SQL("UPDATE t SET v = {opaque}").on('opaque -> anorm.Object(anyVal))
```

## Troubleshooting

This section gathers some errors/warnings you can encounter when using Anorm.

`value SQL is not a member of StringContext`; This compilation error is raised when using the [Anorm interpolation](#SQL-queries-using-String-Interpolation) without the appropriate import.
It can be fixed by adding the package import: `import anorm._`

`type mismatch; found    : T; required : anorm.ParameterValue`; This compilation error occurs when a value of type `T` is passed as parameter, whereas this `T` type is not supported. You need to ensure that a `anorm.ToStatement[T]` and a `anorm.ParameterMetaData[T]` can be found in the implicit scope (see [parameter conversions](#Custom-parameter-conversions)).

On `.executeInsert()`, you can get the error `TypeDoesNotMatch(Cannot convert <value>: class <T> to Long for column ColumnName(<C>)`. This occurs when the [key returned by the database on insertion](http://docs.oracle.com/javase/8/docs/api/java/sql/Statement.html#getGeneratedKeys--) is not compatible with `Long` (the default key parser). It can be fixed by providing the appropriate key parser; e.g. if the database returns a text key: `SQL"...".executeInsert(scalar[String].singleOpt)` (get an `Option[String]` as insertion key).

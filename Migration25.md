# Anorm 2.5 Migration Guide

This is a guide for migrating from Anorm 2.4 to Anorm 2.5. If you need to migrate from an earlier version of Anorm then you must first follow the [Anorm 2.4 Migration Guide](https://github.com/playframework/anorm/blob/main/Migration24.md#anorm-24-migration-guide).

## Type safety

Passing anything different from string or symbol as parameter name is no longer support (previously deprecated `anorm.features.parameterWithUntypedName`).

```scala
val untyped: Any = "name"

// No longer supported (won't compile)
SQL("SELECT * FROM Country WHERE {p}").on(untyped -> "val")
```

Similarly, passing untyped value as parameter is no longer supported (previously deprecated `anorm.features.anyToStatement`).

```scala
val v: Any = "untyped"

// No longer supported (won't compile)
SQL"INSERT INTO table(label) VALUES($v)"
```

It's still possible to pass an opaque value as parameter.
In this case at your own risk, `setObject` will be used on statement.

```scala
val anyVal: Any = myVal
SQL"UPDATE t SET v = ${anorm.Object(anyVal)}"
```

## Type mappings

More conversions are available.

**Numeric types**

Column conversions for numeric types have been improved.

There are new conversions extending column support.

Column (JDBC type) | (as) JVM/Scala type
-------------------|---------------------
Short              | BigInteger
Short              | Long
Short              | Int
Byte               | BigInteger
Byte               | Long
Byte               | Int

**Temporal types**

Column conversions are provided for the following temporal types.

Column (JDBC type)            | (as) JVM/Scala type
------------------------------|---------------------
Date                          | LocalDate<sup>1</sup>
Long                          | LocalDate
Timestamp                     | LocalDate
Timestamp wrapper<sup>2</sup> | LocalDate

- 1. Types `org.joda.time.LocalDate` and `java.time.LocalDate`.
- 2. Any type having the getter `getTimestamp: java.sql.Timestamp`.

## Joda

[Joda](http://www.joda.org) `LocalDateTime` and `LocalDate` are supported as parameter, passed as `Timestamp`. The following JDBC column types can also be parsed as `LocalDateTime` or `LocalDate`: `Date`, `Long`, `Timestamp` or *Wrapper* (any type providing `.getTimestamp`).

## Macros

The macros `namedParser[T]` or `indexedParser[T]` (or `parser[T](names)`) can be used to create a `RowParser[T]` at compile-time, for any case class `T`.

```scala
import anorm.{ Macro, RowParser }

case class Info(name: String, year: Option[Int])

val parser: RowParser[Info] = Macro.namedParser[Info]
val result: List[Info] = SQL"SELECT * FROM list".as(parser.*)
```

## Parsing

The new `SqlParser.folder` make it possible to handle columns that are not strictly defined (e.g. with types that can vary).

```scala
import anorm.{ RowParser, SqlParser }

val parser: RowParser[Map[String, Any]] =
  SqlParser.folder(Map.empty[String, Any]) { (map, value, meta) =>
    Right(map + (meta.column.qualified -> value))
  }

val result: List[Map[String, Any]] = SQL"SELECT * FROM dyn_table".as(parser.*)
```

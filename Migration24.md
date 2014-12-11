# Anorm 2.4 Migration Guide

This is a guide for migrating from Anorm 2.3 to Anorm 2.4. If you need to migrate from an earlier version of Anorm then you must first follow the [Play 2.3 Migration Guide](https://www.playframework.com/documentation/2.3.x/Migration23).

Following [BatchSQL #3016](https://github.com/playframework/playframework/commit/722cd55a3a5369f911f5d11f7c93ba4bf100ca23), `SqlQuery` case class is refactored as a trait with companion object. 
Consequently, `BatchSql` is now created by passed a raw statement which is validated internally.

```scala
import anorm.BatchSql

// Before
BatchSql(SqlQuery("SQL")) // No longer accepted (won't compile)

// Now
BatchSql("SQL")
// Simpler and safer, as SqlQuery is created&validated internally
```

## Parsing

It's now possible to get value from `Row` using column index.

```scala
val res: (String, String) = SQL("SELECT * FROM Test").map(row =>
 row[String](1) -> row[String](2) // string columns #1 and #2
)
```

Column resolution per label is now unified, whatever the label is name or alias.

```scala
val res: (String, Int) = SQL"SELECT text, count AS i".map(row =>
  row[String]("text") -> row[Int]("i")
)
```

New `fold` and `foldWhile` functions to work with result stream.

```scala
val countryCount: Either[List[Throwable], Long] = 
  SQL"Select count(*) as c from Country".fold(0l) { (c, _) => c + 1 }

val books: Either[List[Throwable], List[String]] =
 SQL("Select name from Books").foldWhile(List[String]()) { (list, row) => 
  foldWhile(List[String]()) { (list, row) =>
    if (list.size == 100) (list -> false) // stop with `list`
    else (list := row[String]("name")) -> true // continue with one more name
  }
```

New `withResult` function to provide custom stream parser.

```scala
import anorm.{ Cursor, Row }
@annotation.tailrec
def go(c: Option[Cursor], l: List[String]): List[String] = c match {
  case Some(cursor) => {
    if (l.size == 100) l // custom limit, partial processing
    else {
      val row = it.next()
      go(it, l :+ row[String]("name"))
    }
  }
  case _ => l
}

val books: Either[List[Throwable], List[String]] =
  SQL("Select name from Books").withResult(go(_, List.empty[String]))
```

## Type mappings

More parameter and column conversions are available.

**Array**

A column can be multi-value if its type is JDBC array (`java.sql.Array`). Now Anorm can map it to either array or list (`Array[T]` or `List[T]`), provided type of element (`T`) is also supported in column mapping.

```scala
import anorm.SQL
import anorm.SqlParser.{ scalar, * }

// array and element parser
import anorm.Column.{ columnToArray, stringToArray }

val res: List[Array[String]] =
  SQL("SELECT str_arr FROM tbl").as(scalar[Array[String]].*)
```

New convenient parsing functions are also provided for arrays with `SqlParser.array[T](...)` and `SqlParser.list[T](...)`

In case JDBC statement is expecting an array parameter (`java.sql.Array`), its value can be passed as `Array[T]`, as long as element type `T` is a supported one.

```scala
val arr = Array("fr", "en", "ja")
SQL"UPDATE Test SET langs = $arr".execute()
```

**Multi-value parameter**

New conversions are available to pass `List[T]`, `Set[T]`, `SortedSet[T]`, `Stream[T]` and `Vector[T]` as multi-value parameter.

```scala
SQL("SELECT * FROM Test WHERE cat IN ({categories})").
 on('categories -> List(1, 3, 4)

SQL("SELECT * FROM Test WHERE cat IN ({categories})").
 on('categories -> Set(1, 3, 4)

SQL("SELECT * FROM Test WHERE cat IN ({categories})").
 on('categories -> SortedSet("a", "b", "c")

SQL("SELECT * FROM Test WHERE cat IN ({categories})").
 on('categories -> Stream(1, 3, 4)

SQL("SELECT * FROM Test WHERE cat IN ({categories})").
 on('categories -> Vector("a", "b", "c")
```

**Numeric and boolean types**

Column conversions for basic types like numeric and boolean ones have been improvided.

Some invalid conversions are removed:

Column (JDBC type) | (as) JVM/Scala type
-------------------|---------------------
Double             | Boolean
Int                | Boolean

There are new conversions extending column support.

Column (JDBC type) | (as) JVM/Scala type
-------------------|---------------------
BigDecimal         | BigInteger
BigDecimal         | Int
BigDecimal         | Long
BigInteger         | BigDecimal
BigInteger         | Int
BigInteger         | Long
Boolean            | Int
Boolean            | Long
Boolean            | Short
Byte               | BigDecimal
Float              | BigDecimal
Int                | BigDecimal
Long               | Int
Short              | BigDecimal

**Misc**

- **Binary data**: New column conversions for binary columns (bytes, stream, blob), to be parsed as `Array[Byte]` or `InputStream`.
- **Joda Time**: New conversions for Joda `Instant` or `DateTime`, from `Long`, `Date` or `Timestamp` column.
- Parses text column as `UUID` value: `SQL("SELECT uuid_as_text").as(scalar[UUID].single)`.
- Passing `None` for a nullable parameter is deprecated, and typesafe `Option.empty[T]` must be use instead.

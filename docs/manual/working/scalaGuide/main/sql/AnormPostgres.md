# Anorm for PostgreSQL

This module provides optional conversions for PostgreSQL.

## Add to your project

You will need to add this module to your dependencies (along with Anorm core and JDBC): 

```scala
libraryDependencies ++= Seq(
  "org.playframework.anorm" %% "anorm-postgres" % "{{ site.latest_release }}"
)
```

## JSON

It's possible to pass a `JsValue` or a `JsObject` as parameter. In this case the PostgreSQL `JSONB` type will be used to store the value.

```scala
import java.sql._
import anorm._, postgresql._
import play.api.libs.json._

def jsObjectParameter(implicit con: Connection) = {
  val obj = Json.obj("bar" -> 1)
  SQL"""INSERT INTO test(id, json) VALUES (${"foo"}, $obj)""".executeUpdate()
}
```

The utility `anorm.postgresql.asJson` support the typeclass `Writes`, to store as `JSONB` any Scala type that can be encoded as `JsValue` or `JsObject`.

```scala
import java.sql._
import anorm._, postgresql._
import play.api.libs.json._

sealed trait MyEnum
case object Bar extends MyEnum
case object Lorem extends MyEnum

// Define the typeclass instance
implicit val w: Writes[MyEnum] = Writes[MyEnum] {
  case Bar => Json.obj("bar" -> 1)
  case Lorem => Json.obj("lorem" -> 2)
}

def usingWrites(implicit con: Connection) = {
  val jsonParam = asJson[MyEnum](Lorem)
  SQL"INSERT INTO test(id, json) VALUES(${"x"}, ${jsonParam})".executeUpdate()
}
```

The module also makes it possible to read JSON from PostgreSQL, from column whose types is either `JSONB` or any textual representation (`CLOB`, `VARCHAR`, ...).

```scala
import java.sql._
import anorm._, postgresql._
import play.api.libs.json._

def selectJsValue(implicit con: Connection) =
  SQL"""SELECT json FROM test WHERE id = ${"foo"}""".
    as(SqlParser.scalar[JsValue].single)

def selectJsObject(implicit con: Connection) =
  SQL"""SELECT json FROM test WHERE id = ${"foo"}""".
    as(SqlParser.scalar[JsObject].single)
```

As for the typeclass `Writes`, the `Reads` one to decode JSON is supported using `anorm.postgresql.fromJson`.

```
import java.sql._
import anorm._, postgresql._
import play.api.libs.json._

sealed trait MyEnum
case object Bar extends MyEnum
case object Lorem extends MyEnum

// Define the typeclass instance
implicit val r: Reads[MyEnum] = Reads[MyEnum] { js =>
  (js \ "bar").validate[Int].map {
    case 1 => Bar
    case _ => Lorem
  }
}

def selectFromJson(implicit con: Connection) =
  SQL"""SELECT json FROM test WHERE id = ${"foo"}""".
    as(SqlParser.scalar(fromJson[MyEnum]).single)
```

## UUID

The Java type `java.util.UUID` is provided parameter and column conversions for PostgreSQL.

```
import java.util.UUID
import java.sql._
import anorm._

def insertUUID(implicit con: Connection) = {
  SQL"INSERT INTO test_seq VALUES(${UUID.randomUUID()})".executeUpdate()
  // UUID is passed as PostgreSQL UUID type
}

def selectUUID(implicit con: Connection) =
  SQL"SELECT * FROM test_seq".as(SqlParser.scalar[UUID].*)
```

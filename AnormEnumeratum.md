# Anorm for Enumeratum

This module provides optional conversions for [Enumeratum](https://github.com/lloydmeta/enumeratum).

## Add to your project

You will need to add this module to your dependencies (along with Anorm core and JDBC): 

```scala
libraryDependencies ++= Seq(
  "org.playframework.anorm" %% "anorm-enumeratum" % "{{ site.latest_release }}"
)
```

> As Enumeratum itself is not yet compatible, this module is not available for Scala 3.

## Usage

Using this module, enums can be parsed from database columns and passed as parameters.

### Enum

Considering the following SQL table:

```sql
CREATE TABLE clothes (
  id TEXT,
  shirt TEXT
)
```

[Enum](https://javadoc.io/doc/com.beachape/enumeratum_2.13/latest/enumeratum/Enum.html) values can be defined to be compatible with the text column.

```scala
import enumeratum.{ Enum, EnumEntry }
import anorm.enumeratum.AnormEnum

sealed trait ShirtSize extends EnumEntry

case object ShirtSize extends Enum[ShirtSize] with AnormEnum[ShirtSize] {
  case object Small  extends ShirtSize
  case object Medium extends ShirtSize
  case object Large  extends ShirtSize
  val values = findValues
}

case class Shirt(size: ShirtSize)
```

Then Anorm can parsed the column in query result and passed it as parameter.

```scala
import java.sql.Connection

import anorm._

// Support Enum as readable Column
def findShirtSize(shirtId: String)(implicit con: Connection): ShirtSize =
  SQL"SELECT size FROM shirt_tbl WHERE id = $shirtId".
    as(SqlParser.scalar[ShirtSize].single)

// Support Enum as parameter
def updateShirtSize(shirtId: String, size: ShirtSize)(
  implicit con: Connection) =
    SQL"UPDATE shirt_tbl SET size = ${size} WHERE id = ${shirtId}".execute()
```

### ValueEnum

Considering the following SQL table:

```sql
CREATE TABLE clothes (
  id TEXT,
  shirt SMALLINT
)
```

[ValueEnum](https://javadoc.io/doc/com.beachape/enumeratum_2.13/latest/enumeratum/values/ValueEnum.html) values can be defined to be compatible with the `SMALLINT` column.

```scala
import enumeratum.values.{ IntEnum, IntEnumEntry }
import anorm.enumeratum.values.IntAnormValueEnum

sealed abstract class ShirtSize(val value: Int) extends IntEnumEntry

case object ShirtSize extends IntEnum[ShirtSize]
  with IntAnormValueEnum[ShirtSize] {

  case object Small  extends ShirtSize(1)
  case object Medium extends ShirtSize(2)
  case object Large  extends ShirtSize(3)

  val values = findValues
}

case class Shirt(size: ShirtSize)
```

Then Anorm can parsed the column in query result and passed it as parameter.

```scala
import java.sql.Connection
import anorm._

// Support ValueEnum as readable Column
def findShirtSize(shirtId: String)(implicit con: Connection): ShirtSize =
  SQL"SELECT size FROM shirt_tbl WHERE id = $shirtId".
    as(SqlParser.scalar[ShirtSize].single)

// Support ValueEnum as parameter
def updateShirtSize(shirtId: String, size: ShirtSize)(
  implicit con: Connection) =
    SQL"UPDATE shirt_tbl SET size = ${size} WHERE id = ${shirtId}".execute()
```
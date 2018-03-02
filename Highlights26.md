# What's new in Anorm 2.6

This page highlights the new features of Anorm 2.6. If you want learn about the changes you need to make to migrate to Anorm 2.6, check out the [[Migration Guide|Migration26]].

The [streaming support](Migration26.html#streaming) has been improved for a reactive processing of the results, with new modules:

- Akka Stream module
- Iteratees module
- New typeclass [`ToParameterList`](Migration26.html#ToParameterList)
- New module dedicated to PostgreSQL
- Macros upgrade;
  - New macro to generate positional parser with an offset: `offsetParser[T](offset: Int)`
  - New macro to materialize parser using `ColumnNaming`: `namedParser[T]`
  - The parsing macros can now use existing `RowParser` as sub-parser.
  - New macros `valueColumn` and `valueToStatement` for the [Value Classes](https://docs.scala-lang.org/overviews/core/value-classes.html).
- `ColumnAliaser` to define user aliases over the results, before parsing.
- Operation `.executeInsert1` allows to select columns among the generated keys.
- Scala for 2.11 & 2.12, and Java compatibility 8 & 9

# What's new in Anorm 2.6

This page highlights the new features of Anorm 2.6. If you want learn about the changes you need to make to migrate to Anorm 2.6, check out the [[Migration Guide|Migration25]].

- Iteratees module
- The new operation `.executeInsert1` allows to select columns among the generated keys.
- New macro `offsetParser[T](offset: Int)`.
- The parsing macros can now use existing `RowParser` as sub-parser.
- The `ColumnAliaser` to define user aliases over the results, before parsing.
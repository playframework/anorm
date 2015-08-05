# What's new in Anorm 2.5

This page highlights the new features of Anorm 2.5. If you want learn about the changes you need to make to migrate to Anorm 2.5, check out the [[Migration Guide|Migration25]].

- Row parser automatically generated for case classes: `Macro.namedParser[T]`, `Macro.indexedParser[T]` and `Macro.parser[T](names)`.
- New `SqlParser.folder` to fold over "non-strict" row columns.
- New numeric and temporal conversions.

<!--- Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com> -->

# What's new in Anorm 2.4

This page highlights the new features of Anorm 2.4. If you want learn about the changes you need to make to migrate to Anorm 2.4, check out the [[Migration Guide|Migration24]].

- Improved statement preparation & string interpolation: new `#$value` syntax and better performance.
- New positional getter on `Row`.
- Unified column resolution by label, whatever it is (name or alias).
- New streaming API; Functions `fold` and `foldWhile` to work with result stream (e.g. `SQL"Select count(*) as c from Country".fold(0l) { (c, _) => c + 1 }`). Function `withResult` to provide custom stream parser (e.g. `SQL("Select name from Books").withResult(customTailrecParser(_, List.empty[String]))`).
- Supports array (`java.sql.Array`) from column (e.g. `SQL("SELECT str_arr FROM tbl").as(scalar[Array[String]].*)`) or as parameter (e.g. `SQL"""UPDATE Test SET langs = ${Array("fr", "en", "ja")}""".execute()`).
- Improved conversions for numeric and boolean columns.
- New conversions for binary columns (bytes, stream, blob), to parsed them as `Array[Byte]` or `InputStream`.
- New conversions for temporal types. For Java8 `Instant`, `LocalDateTime`, `ZonedDateTime`, Joda `Instant` or `DateTime`, from `Long`, `Date` or `Timestamp` column.
- Added conversions to support `List[T]`, `Set[T]`, `SortedSet[T]`, `Stream[T]` and `Vector[T]` as multi-value parameter.
- New conversion to parse text column as UUID (e.g. `SQL("SELECT uuid_as_text").as(scalar[java.util.UUID].single)`).

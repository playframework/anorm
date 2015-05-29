# Anorm 2.5 Migration Guide

This is a guide for migrating from Anorm 2.4 to Anorm 2.5. If you need to migrate from an earlier version of Anorm then you must first follow the [Anorm 2.4 Migration Guide](https://github.com/playframework/anorm/blob/master/Migration24.md#anorm-24-migration-guide).

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

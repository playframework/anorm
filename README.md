# Anorm

Anorm is a simple data access layer that uses plain SQL to interact with the database and provides an API to parse and transform the resulting datasets.

- [User guide](https://playframework.github.io/anorm/)
- [Scaladoc](https://playframework.github.io/anorm/unidoc/anorm/)

## Usage

In a projects built with SBT, dependency to Anorm can be added as following:

```scala
libraryDependencies ++= Seq(
  "org.playframework.anorm" %% "anorm" % ReplaceByAnormVersion)
```

## Build manually

Anorm can be built from this source repository.

    sbt publish-local

To run the tests, use:

    sbt test

[CircleCI](https://circleci.com/gh/playframework/anorm): ![CircleCI build status](https://circleci.com/gh/playframework/anorm.png?branch=main)

## Documentation

To run the documentation server, run:

    sbt docs/run

To test the documentation code samples, run:

    sbt docs/test

## Support

The Anorm library is *[Community Driven][]*.

[Community Driven]: https://developer.lightbend.com/docs/lightbend-platform/introduction/getting-help/support-terminology.html#community-driven

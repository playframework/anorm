# Anorm contributor guidelines

## Reporting Issues

If you wish to report an issue for Anorm, please ensure you have done the following things:

Before making a contribution, it is important to make sure that the change you wish to make and the approach you wish to take will likely be accepted, otherwise you may end up doing a lot of work for nothing.  If the change is only small, for example, if it's a documentation change or a simple bugfix, then it's likely to be accepted with no prior discussion.  However, new features, or bigger refactorings should first be discussed on the [developer mailing list](https://groups.google.com/forum/#!forum/play-framework-dev).  Additionally, any issues with the [community label](https://github.com/playframework/anorm/issues?q=is%3Aopen+is%3Aissue+label%3Acommunity) have been agreed to be a change that will likely be accepted.

## Procedure

This is the process for a contributor (that is, a non Anorm core developer) to contribute to Anorm.

1. Make sure you have signed the [Typesafe CLA](http://www.typesafe.com/contribute/cla); if not, sign it online.
2. Ensure that your contribution meets the following guidelines:
    1. Live up to the current code standard:
        - Not violate [DRY](http://programmer.97things.oreilly.com/wiki/index.php/Don%27t_Repeat_Yourself).
        - [Boy Scout Rule](http://programmer.97things.oreilly.com/wiki/index.php/The_Boy_Scout_Rule) needs to have been applied.
    2. Regardless of whether the code introduces new features or fixes bugs or regressions, it must have comprehensive tests. This includes when modifying existing code that isn't tested.
    3. The code must be well documented in the Anorm standard documentation format (see the [documentation](https://playframework.com/documentation/latest/ScalaAnorm) section below). Each API change must have the corresponding documentation change.
    4. Implementation-wise, the following things should be avoided as much as possible:
        * Global state
        * Public mutable state
        * Implicit conversions
        * ThreadLocal
        * Locks
        * Casting
        * Introducing new, heavy external dependencies
    5. New files must:
       * Have a Typesafe copyright header in the style of ``Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>``.
       * Not use ``@author`` tags since it does not encourage [Collective Code Ownership](http://www.extremeprogramming.org/rules/collective.html).
3. Ensure that your commits are squashed.  See [working with git](https://playframework.com/documentation/latest/WorkingWithGit) for more information.
4. Submit a pull request.

If the pull request does not meet the above requirements then the code should **not** be merged into master, or even reviewed - regardless of how good or important it is. No exceptions.

The pull request will be reviewed according to the [implementation decision process](https://playframework.com/community-process#Implementation-decisions).
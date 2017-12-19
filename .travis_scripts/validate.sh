#! /bin/bash

sbt -Dsbt.scala.version=2.10.7 ++$TRAVIS_SCALA_VERSION scalariformFormat test:scalariformFormat > /dev/null
git diff --exit-code || (cat >> /dev/stdout <<EOF
[ERROR] Scalariform check failed, see differences above.
To fix, format your sources using sbt scalariformFormat test:scalariformFormat before submitting a pull request.
Additionally, please squash your commits (eg, use git commit --amend) if you're going to update this pull request.
EOF
    false
)

sbt -Dsbt.scala.version=2.10.7 ++$TRAVIS_SCALA_VERSION publishLocal mimaReportBinaryIssues test docs/test

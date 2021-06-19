#! /bin/bash

sbt ++$TRAVIS_SCALA_VERSION scalariformFormat test:scalariformFormat scalafixAll > /dev/null
git diff --exit-code || (cat >> /dev/stdout <<EOF
[ERROR] Scalariform check failed, see differences above.
To fix, format your sources using sbt scalariformFormat test:scalariformFormat before submitting a pull request.
Additionally, please squash your commits (eg, use git commit --amend) if you're going to update this pull request.
EOF
    false
)

SBT_TASKS="publishLocal mimaReportBinaryIssues test"

if [ ! "v$TRAVIS_SCALA_VERSION" = "v2.11.12" ]; then
  SBT_TASKS="$SBT_TASKS docs/test doc"
fi

if [ "v$TRAVIS_SCALA_VERSION" = "v2.13.5" ]; then
  SBT_TASKS="$SBT_TASKS scapegoat"
fi

sbt ++$TRAVIS_SCALA_VERSION $SBT_TASKS

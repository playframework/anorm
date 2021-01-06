#! /bin/bash

sbt ++$TRAVIS_SCALA_VERSION scalariformFormat test:scalariformFormat scalafixAll > /dev/null
git diff --exit-code || (cat >> /dev/stdout <<EOF
[ERROR] Scalariform check failed, see differences above.
To fix, format your sources using sbt scalariformFormat test:scalariformFormat before submitting a pull request.
Additionally, please squash your commits (eg, use git commit --amend) if you're going to update this pull request.
EOF
    false
)

SBT_TASKS="publishLocal mimaReportBinaryIssues test docs/test doc"

if [ "v$TRAVIS_SCALA_VERSION" = "2.12.12" ]; then
  SBT_TASKS="$SBT_TASKS scapegoat"
fi

sbt ++$TRAVIS_SCALA_VERSION $SBT_TASKS

if [ "branch:$TRAVIS_BRANCH" = "branch:master" ]; then
  echo "Publishing snapshot ..."

  sbt ";set publishTo := Some(\"Sonatype Nexus Repository Manager\" at \"https://oss.sonatype.org/content/repositories/snapshots\") ;set credentials += Credentials(\"Sonatype Nexus Repository Manager\", \"oss.sonatype.org\", \"$SONATYPE_USER\", \"$SONATYPE_PASSWORD\") ;publish"
fi

#! /bin/bash

set -e

SCRIPT_DIR=`dirname $0 | sed -e "s|^\./|$PWD/|"`

source "$SCRIPT_DIR/jvmopts.sh"

sbt ++$SCALA_VERSION scalariformFormat test:scalariformFormat > /dev/null #scalafixAll
git diff --exit-code || (cat >> /dev/stdout <<EOF
[ERROR] Scalariform check failed, see differences above.
To fix, format your sources using sbt scalariformFormat test:scalariformFormat before submitting a pull request.
Additionally, please squash your commits (eg, use git commit --amend) if you're going to update this pull request.
EOF
    false
)

SBT_TASKS="publishLocal mimaReportBinaryIssues test docs/test"

#TODO: if [ "v$SCALA_VERSION" = "v2.12.15" ]; then
#  SBT_TASKS="$SBT_TASKS scapegoat"
#fi

sbt ++$SCALA_VERSION $SBT_TASKS

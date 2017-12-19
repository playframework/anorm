#! /bin/sh

# Execute `sbt clean +publish-local` before

REPO="https://oss.sonatype.org/service/local/staging/deploy/maven2/"

if [ $# -lt 2 ]; then
    echo "Usage $0 version gpg-key"
    exit 1
fi

VERSION="$1"
KEY="$2"

echo "Password: "
read -s PASS

function deploy {
  BASE="$1"
  POM="$BASE.pom"
  FILES="$BASE.jar $BASE-javadoc.jar:javadoc $BASE-sources.jar:sources"

  for FILE in $FILES; do
    JAR=`echo "$FILE" | cut -d ':' -f 1`
    CLASSIFIER=`echo "$FILE" | cut -d ':' -f 2`

    if [ ! "$CLASSIFIER" = "$JAR" ]; then
      ARG="-Dclassifier=$CLASSIFIER"
    else
      ARG=""
    fi

    expect << EOF
set timeout 300
spawn mvn gpg:sign-and-deploy-file -Dkeyname=$KEY -DpomFile=$POM -Dfile=$JAR $ARG -Durl=$REPO -DrepositoryId=sonatype-nexus-staging
expect "GPG Passphrase:"
send "$PASS\r"
expect "BUILD SUCCESS"
expect eof
EOF
  done
}

SCALA_MODULES="core:anorm tokenizer:anorm-tokenizer akka:anorm-akka iteratee:anorm-iteratee postgres:anorm-postgres"
SCALA_VERSIONS="2.11 2.12"
BASES=""

for V in $SCALA_VERSIONS; do
    for M in $SCALA_MODULES; do
        B=`echo "$M" | cut -d ':' -f 1`
        SCALA_DIR="$B/target/scala-$V"

        if [ ! -d "$SCALA_DIR" ]; then
            echo "Skip Scala version $V for $M"
        else
            N=`echo "$M" | cut -d ':' -f 2`
            BASES="$BASES $SCALA_DIR/$N"_$V-$VERSION
        fi
    done
done

for B in $BASES; do
  deploy "$B"
done

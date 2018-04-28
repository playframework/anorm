#!/usr/bin/env bash

set -e

# Preprocess the manual pages for Jekyll
LATEST=`grep '^latest_release' _config.yml | cut -d ':' -f 2 | sed -e 's/ //g'`

if [ ! -d anorm ]; then
  git clone git@github.com:playframework/anorm.git --branch "$LATEST"
else
  echo "Documentation already generated."
  echo '(Remove the `anorm` directory to clean it)'
  cd anorm && git pull origin "$LATEST" && cd -
fi

SRCDIR="anorm/docs/manual/working/scalaGuide/main/sql"

TITLE=`grep '^# ' "$SRCDIR/ScalaAnorm.md" | sed -e 's/^# //'`

echo "Prepare the documentation index"
cat > index.md << EOF
---
layout: default
title: $TITLE
---

EOF

echo "Prepare the named snippets"
TMPD=`mktemp -d`

for R in `grep '(code/' "$SRCDIR/ScalaAnorm.md" | grep '.scala' | sed -e 's/^@\[//;s/\](/:/;s/)$//'`; do
  REF=`echo "$R" | cut -d ':' -f 1`
  SRC=`echo "$R" | cut -d ':' -f 2`

  echo "$REF @ docs/manual/working/scalaGuide/main/sql/$SRC"

  awk -v "ref=$REF" 'BEGIN { \
    h = 0; \
    needle = "//#" ref;\
    ind = 0;\
  } { \
    if (index($0, needle) > 0) { \
      if (h == 0) {\
        h = 1;\
        prefix = gensub(/^([ \t]+).*$/, "\\1", "g", $0);\
        ind = length(prefix) + 1;\
      } else { h = 0 } \
    } else if (h == 1) { \
      printf("%s\n", substr($0, ind)) \
    } \
  }' < "$SRCDIR/$SRC" > "$TMPD/$REF"
done


awk -v "tmpd=$TMPD" 'BEGIN { h = 0; } { \
  if (index($0, "@[") == 1) {\
    ref = gensub(/^@\[([a-zA-Z0-9]+).*$/, "\\1", "g", $0);\
    f = tmpd "/" ref;\
    print("{% highlight scala %}");\
    while ((getline line<f) > 0) {\
      print line\
    }\
    print("{% endhighlight %}");\
  } else if (substr($0, 1, 3) == "```") {\
    if (h == 0) {\
      lang = substr($0, 4);\
      print("{% highlight " substr($0, 4) " %}");\
      h = 1;\
    } else {\
      print("{% endhighlight %}");\
      h = 0;\
    }\
  } else if (index($0, "## Executing SQL queries") == 1) {\
    while ((getline line<"_toc") > 0) {\
      print line\
    }\
    printf("\n*See [release notes](Highlights.html)*\n\n%s\n", $0)\
  } else { print($0); }\
}' "$SRCDIR/ScalaAnorm.md" | sed -e 's/@\[/[/;s/{% highlight[ \t]*%}/{% highlight text %}/;s/"org.playframework.anorm" %% "anorm" % "[^"]*"/"org.playframework.anorm" %% "anorm" % "2.6.2"/' | grep -v '<!---' | sed -e 's#\[\[on the Scala database page \| ScalaDatabase\]\]#[on the Scala database page](https://playframework.com/documentation/latest/ScalaDatabase)#' >> index.md

rm -rf code && cp -R "$SRCDIR/code" code

# PostgreSQL
cp "$SRCDIR/AnormPostgres.md" .
git add AnormPostgres.md

# Contributing page
cp anorm/CONTRIBUTING.md .
git add CONTRIBUTING.md

# Highlights
rm -f Highlights.md

for MD in `ls -v -1 anorm/Highlights*.md | sort -n -r`; do
  # Skip latest (0 with reverse)
  perl -pe 's/\[\[([^|]+)\|([^\]]+)\]\]/[\1](\2.html)/g' < $MD >> Highlights.md

  echo '' >> Highlights.md
done

git add Highlights.md

# Migration
cp anorm/Migration*.md .
git add Migration*.md

#! /bin/sh

# Preprocess the manual pages for Jekyll

if [ ! -d anorm ]; then
  git clone git@github.com:playframework/anorm.git --branch master
fi

SRCDIR="anorm/docs/manual/working/scalaGuide/main/sql"

TITLE=`grep '^# ' "$SRCDIR/ScalaAnorm.md" | sed -e 's/^# //'`

cat > index.md << EOF
---
layout: default
title: $TITLE
---

EOF

awk 'BEGIN { h = 0; } { if (substr($0, 1, 3) == "```") { if (h == 0) { lang = substr($0, 4); print("{% highlight " substr($0, 4) " %}"); h = 1; } else { print("{% endhighlight %}"); h = 0; } } else { print($0); } }' "$SRCDIR/ScalaAnorm.md" | sed -e 's/@\[/[/;s/{% highlight[ \t]*%}/{% highlight text %}/' | grep -v '<!---' | sed -e 's#\[\[on the Scala database page \| ScalaDatabase\]\]#[on the Scala database page](https://playframework.com/documentation/latest/ScalaDatabase)#' >> index.md

rm -rf code && cp -R "$SRCDIR/code" code

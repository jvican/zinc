#!/usr/bin/env bash
set -o nounset

if ! sbt -sbt-dir /drone/.sbt/1.0 -sbt-boot /drone/.sbt/boot "$@"; then
  exit 1
fi

find "/root/.ivy2/cache"     -name "ivydata-*.properties" -print -delete
find "/root/.coursier"       -name "ivydata-*.properties" -print -delete
find "/root/.sbt"            -name "*.lock"               -print -delete

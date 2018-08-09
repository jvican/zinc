#!/usr/bin/env bash
set -eu
set -o nounset
SCALA_VERSION="$1"

THIS_DIR=$(dirname "$0")
SBT_SCRIPT="$THIS_DIR/sbt-ci.sh"
"$SBT_SCRIPT" -Dfile.encoding=UTF-8 \
  -J-XX:ReservedCodeCacheSize=256M \
  -J-Xmx4086M -J-Xms1024M -J-server \
  scalafmt::test \
  test:scalafmt::test \
  compilerInterfaceJava6Compat/compile \
  zincRoot/test:compile \
  bloopScripted/compile \
  crossTestBridges \
  "publishBridgesAndSet $SCALA_VERSION" \
  zincRoot/test \
  zincRoot/scripted

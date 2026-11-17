#!/usr/bin/env bash
# Runs the pure-Java unit tests (no Android SDK needed). Exit code != 0 on failure.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
S=app/src/main/java/io/github/samaliyan/timeatlas
javac -encoding UTF-8 -Xlint:all -Werror -d "$OUT" "$S/City.java" "$S/CityData.java" "$S/CityDb.java" "$S/CityCodec.java" "$S/TimeMath.java" tests/java/io/github/samaliyan/timeatlas/CoreTest.java
java -Dstdout.encoding=UTF-8 -cp "$OUT" io.github.samaliyan.timeatlas.CoreTest

#!/usr/bin/env sh
# CloudStore - compile and run the core tests (no database needed).
# Usage:  ./test.sh
set -e
cd "$(dirname "$0")"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; *) SEP=":" ;; esac
rm -rf out-test
javac -encoding UTF-8 -d out-test -cp "lib/*" src/cloudstore/*.java tests/cloudstore/*.java
exec java -cp "out-test${SEP}lib/*" cloudstore.CoreTest

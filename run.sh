#!/usr/bin/env sh
# Kasi-Crunch - build and run on macOS / Linux / Git Bash.
# Usage:  ./run.sh
set -e
cd "$(dirname "$0")"

DRIVER_VERSION=8.4.0
DRIVER="lib/mysql-connector-j-$DRIVER_VERSION.jar"
if [ ! -f "$DRIVER" ]; then
  echo "Downloading the MySQL JDBC driver $DRIVER_VERSION..."
  mkdir -p lib
  curl -fsSL -o "$DRIVER" "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/$DRIVER_VERSION/mysql-connector-j-$DRIVER_VERSION.jar"
fi

if [ ! -f config.properties ]; then
  cp config.example.properties config.properties
  echo "Created config.properties - add your MySQL password to it, then run this script again."
  exit 1
fi

# Java uses ';' between classpath entries on Windows and ':' elsewhere.
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; *) SEP=":" ;; esac

echo "Compiling..."
rm -rf out
javac -encoding UTF-8 -d out -cp "lib/*" src/kasicrunch/*.java

echo "Starting Kasi-Crunch..."
exec java -cp "out${SEP}lib/*" kasicrunch.Main

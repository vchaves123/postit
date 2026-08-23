#!/usr/bin/env bash
# Compila e roda as checagens contra o jar. Rode "mvn package" antes.
set -e
dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
jar="$dir/target/recados.jar"
out="$dir/target/checks"
if [ ! -f "$jar" ]; then
  echo "Jar nao encontrado. Rode: mvn package" >&2
  exit 1
fi
mkdir -p "$out"
javac -cp "$jar" -d "$out" "$dir"/checks/*.java
java -cp "$jar:$out" Checks

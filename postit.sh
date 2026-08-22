#!/usr/bin/env bash
# Inicia o postit. Rode "mvn package" antes, se o jar nao existir.
dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ ! -f "$dir/target/postit.jar" ]; then
  echo "Jar nao encontrado. Rode: mvn package" >&2
  exit 1
fi
exec java -jar "$dir/target/postit.jar"

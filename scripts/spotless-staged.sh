#!/usr/bin/env bash
# Formata só os arquivos Java passados (os staged, pelo lefthook). O Spotless
# recebe uma lista de regex de caminho absoluto em -DspotlessFiles.
set -euo pipefail

[ "$#" -eq 0 ] && exit 0

patterns=()
for file in "$@"; do
  escaped="$(printf '%s' "$file" | sed 's/[][\.^$*+?(){}|]/\\&/g')"
  patterns+=(".*${escaped}")
done

files="$(IFS=,; printf '%s' "${patterns[*]}")"
exec ./mvnw -q -B -ntp spotless:apply -DspotlessFiles="$files"

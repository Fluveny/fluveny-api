#!/usr/bin/env bash
# Chama POST /local/<seed|reset> na API que o `mise run up` subiu. O banco do
# perfil local vive dentro do processo da API (Testcontainers), então não há
# outro caminho até ele.
set -euo pipefail

action="${1:-}"
case "$action" in
  seed | reset) ;;
  *)
    echo "uso: $0 seed|reset" >&2
    exit 2
    ;;
esac

base_url="${FLUVENY_API_URL:-http://localhost:8080}"
body="$(mktemp)"
trap 'rm -f "$body"' EXIT

if ! status="$(curl -sS --max-time 120 -o "$body" -w '%{http_code}' -X POST "$base_url/local/$action" 2>/dev/null)"; then
  echo "A API não está no ar em $base_url. Suba com \`mise run up\` em outro terminal e rode de novo." >&2
  exit 1
fi

# Fora do perfil local, /local/* não existe e cai no deny-by-default (401/403) ou no 404.
if [ "$status" = "401" ] || [ "$status" = "403" ] || [ "$status" = "404" ]; then
  echo "A API em $base_url respondeu $status: ela não está no perfil local. Suba com \`mise run up\`." >&2
  exit 1
fi

if [ "$status" -ge 400 ]; then
  echo "A API respondeu $status:" >&2
  cat "$body" >&2
  echo >&2
  exit 1
fi

cat "$body"
echo

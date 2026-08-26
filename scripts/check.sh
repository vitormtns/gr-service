#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
"$SCRIPT_DIR/../mvnw" verify

for option in "$@"; do
  case "$option" in
    --supabase-status)
      npm --prefix "$SCRIPT_DIR/.." run supabase:status
      ;;
    --supabase-reset)
      npm --prefix "$SCRIPT_DIR/.." run supabase:reset
      ;;
    *)
      echo "Opção inválida: $option" >&2
      exit 2
      ;;
  esac
done

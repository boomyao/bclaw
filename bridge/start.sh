#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f package.json ]; then
  echo "bridge/package.json not found" >&2
  exit 1
fi

if [ ! -d node_modules/ws ]; then
  echo "Missing dependency: ws" >&2
  echo "Run 'npm install' inside bridge/ first." >&2
  exit 1
fi

exec node server.js

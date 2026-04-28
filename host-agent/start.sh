#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f package.json ]; then
  echo "host-agent/package.json not found" >&2
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  for candidate in \
    "${NVM_DIR:-${HOME}/.nvm}"/versions/node/*/bin/node \
    "${HOME}/.volta/bin/node" \
    "${HOME}/.local/bin/node"; do
    if [ -x "$candidate" ]; then
      export PATH="$(dirname "$candidate"):${PATH}"
      break
    fi
  done
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Missing runtime: node" >&2
  echo "Run '../scripts/install-macos-host-agent' or '../scripts/install-linux-host-agent' from host-agent/ or install Node.js 18+." >&2
  exit 1
fi

exec node server.js

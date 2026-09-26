#!/usr/bin/env bash
set -euo pipefail

# End-to-end test: generates a client from tests/echo.yaml, points it at a local
# echo server and asserts on what the server actually received. Hermetic — no
# outbound network.
#
# Usage: OSCRIPT=/path/to/oscript scripts/smoke.sh

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OSCRIPT="${OSCRIPT:-oscript}"
PYTHON="${PYTHON:-python3}"
PORT="${PORT:-8731}"

cd "${REPO_ROOT}"

rm -rf out-smoke
./scripts/generate.sh tests/echo.yaml out-smoke packageName=echo-client packageVersion=1.0.0

"${PYTHON}" tests/echo-server.py "${PORT}" &
SERVER_PID=$!
trap 'kill "${SERVER_PID}" 2>/dev/null || true' EXIT

# Wait for the port to accept connections rather than sleeping a fixed amount.
for _ in $(seq 1 50); do
  if "${PYTHON}" -c "import socket,sys; s=socket.socket(); sys.exit(0 if s.connect_ex(('127.0.0.1',${PORT}))==0 else 1)" 2>/dev/null; then
    break
  fi
  sleep 0.1
done

"${OSCRIPT}" tests/smoke.os

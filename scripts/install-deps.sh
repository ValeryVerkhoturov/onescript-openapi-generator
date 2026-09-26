#!/usr/bin/env bash
set -euo pipefail

# Installs the libraries generated code depends on:
#
#   1connector — the HTTP transport
#   jsonschema — validating models against their embedded JSON Schema
#
# Needed before compile-checking or running generated code, not just at runtime:
# ТранспортHTTP.os and every model open with `#Использовать`, so both libraries
# have to resolve even for `oscript -check`.
#
# Usage: OSCRIPT=/path/to/oscript scripts/install-deps.sh

OSCRIPT="${OSCRIPT:-oscript}"
CONNECTOR_VERSION="${CONNECTOR_VERSION:-2.3.3}"
JSONSCHEMA_VERSION="${JSONSCHEMA_VERSION:-0.1.0}"

if ! command -v "${OSCRIPT}" >/dev/null 2>&1 && [[ ! -x "${OSCRIPT}" ]]; then
  echo "oscript not found — set OSCRIPT=/path/to/oscript" >&2
  exit 1
fi

ONESCRIPT_HOME="$(cd -- "$(dirname -- "$(command -v "${OSCRIPT}" || echo "${OSCRIPT}")")/.." && pwd)"
OPM="${ONESCRIPT_HOME}/lib/opm/src/cmd/opm.os"

install_package() {
  local name="$1" version="$2"

  if [[ -d "${ONESCRIPT_HOME}/lib/${name}" ]]; then
    echo "  ✓ ${name} already installed"
    return 0
  fi

  "${OSCRIPT}" "${OPM}" install "${name}@${version}"
  echo "  ✓ ${name} ${version} installed"
}

install_package 1connector "${CONNECTOR_VERSION}"
install_package jsonschema "${JSONSCHEMA_VERSION}"

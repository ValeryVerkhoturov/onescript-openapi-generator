#!/usr/bin/env bash
set -euo pipefail

# Installs the libraries generated code depends on:
#
#   1connector — the HTTP transport
#   jason      — serialises models to JSON from their annotations
#
# jason depends on validate and opm installs it alongside: the &Тип and
# &ДляКаждого annotations jason reads are defined there.
#
# Needed for compile-checking, not just at runtime: ТранспортHTTP.os and every
# model open with `#Использовать`, so `oscript -check` has to resolve them too.
#
# Usage: OSCRIPT=/path/to/oscript scripts/install-deps.sh

OSCRIPT="${OSCRIPT:-oscript}"
CONNECTOR_VERSION="${CONNECTOR_VERSION:-2.3.3}"
JASON_VERSION="${JASON_VERSION:-0.6.0}"

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
install_package jason "${JASON_VERSION}"

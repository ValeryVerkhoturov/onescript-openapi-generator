#!/usr/bin/env bash
set -euo pipefail

# Installs 1connector, the HTTP library the generated transport is built on.
#
# Needed before compile-checking or running generated code: ТранспортHTTP.os
# starts with `#Использовать 1connector`, so the library has to be resolvable
# even for `oscript -check`.
#
# Usage: OSCRIPT=/path/to/oscript scripts/install-connector.sh [version]

OSCRIPT="${OSCRIPT:-oscript}"
VERSION="${1:-2.3.3}"

if ! command -v "${OSCRIPT}" >/dev/null 2>&1 && [[ ! -x "${OSCRIPT}" ]]; then
  echo "oscript not found — set OSCRIPT=/path/to/oscript" >&2
  exit 1
fi

ONESCRIPT_HOME="$(cd -- "$(dirname -- "$(command -v "${OSCRIPT}" || echo "${OSCRIPT}")")/.." && pwd)"
OPM="${ONESCRIPT_HOME}/lib/opm/src/cmd/opm.os"

if [[ -d "${ONESCRIPT_HOME}/lib/1connector" ]]; then
  echo "  ✓ 1connector already installed"
  exit 0
fi

"${OSCRIPT}" "${OPM}" install "1connector@${VERSION}"
echo "  ✓ 1connector ${VERSION} installed"

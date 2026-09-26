#!/usr/bin/env bash
set -uo pipefail

# Syntax-checks every generated .os file with the OneScript compiler.
#
# `oscript -check` compiles the module without running it and exits non-zero on
# the first error, which is the only compile gate OneScript offers.
#
# Usage: scripts/check.sh <dir> [more dirs ...]
#   OSCRIPT=/path/to/oscript scripts/check.sh out/

OSCRIPT="${OSCRIPT:-oscript}"

if ! command -v "${OSCRIPT}" >/dev/null 2>&1 && [[ ! -x "${OSCRIPT}" ]]; then
  echo "oscript not found — set OSCRIPT=/path/to/oscript" >&2
  exit 1
fi

total=0
failed=0

for target in "$@"; do
  while IFS= read -r file; do
    total=$((total + 1))
    if ! output="$("${OSCRIPT}" -check "${file}" 2>&1)" || [[ "${output}" != "No errors." ]]; then
      failed=$((failed + 1))
      echo "FAIL ${file}"
      echo "${output}" | head -3
    fi
  done < <(find "${target}" -name '*.os' -type f | sort)
done

echo "checked ${total} file(s), ${failed} failed"
[[ ${failed} -eq 0 ]]

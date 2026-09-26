#!/usr/bin/env bash
set -euo pipefail

# Generates a OneScript client from an OpenAPI document using the plugin jar.
#
# The plugin is loaded next to the stock openapi-generator-cli jar: Java's
# ServiceLoader merges both META-INF/services entries, so `-g onescript` resolves
# exactly like a built-in generator. Everything runs in the official image so no
# local JDK is needed.
#
# Usage: scripts/generate.sh <spec> <output-dir> [key=value ...]

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <spec> <output-dir> [additional-properties ...]" >&2
  exit 2
fi

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OPENAPI_GENERATOR_VERSION="${OPENAPI_GENERATOR_VERSION:-v7.10.0}"

SPEC="$(cd -- "$(dirname -- "$1")" && pwd)/$(basename -- "$1")"
OUT="$2"
shift 2

PLUGIN_JAR="$(ls "${REPO_ROOT}"/target/onescript-openapi-generator-*.jar 2>/dev/null | head -1 || true)"
if [[ -z "${PLUGIN_JAR}" ]]; then
  echo "plugin jar not built — run scripts/build.sh first" >&2
  exit 1
fi

mkdir -p "${OUT}"
OUT="$(cd -- "${OUT}" && pwd)"

EXTRA=""
if [[ $# -gt 0 ]]; then
  EXTRA="--additional-properties=$(IFS=,; echo "$*")"
fi

docker run --rm -u "$(id -u):$(id -g)" \
  -v "${REPO_ROOT}/target:/plugin:ro" \
  -v "$(dirname "${SPEC}"):/specs:ro" \
  -v "${OUT}:/out" \
  --entrypoint java \
  "openapitools/openapi-generator-cli:${OPENAPI_GENERATOR_VERSION}" \
  -cp "/plugin/$(basename "${PLUGIN_JAR}"):/opt/openapi-generator/modules/openapi-generator-cli/target/openapi-generator-cli.jar" \
  org.openapitools.codegen.OpenAPIGenerator generate \
  -g onescript \
  -i "/specs/$(basename "${SPEC}")" \
  -o /out \
  --skip-validate-spec \
  ${EXTRA} \
  >/dev/null

echo "generated ${OUT}"

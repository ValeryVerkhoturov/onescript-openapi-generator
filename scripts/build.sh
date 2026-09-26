#!/usr/bin/env bash
set -euo pipefail

# Builds the plugin jar inside the Maven image so contributors need no local JDK.

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9-eclipse-temurin-17}"
MAVEN_REPO="${MAVEN_REPO:-${HOME}/.m2-onescript-codegen}"

mkdir -p "${MAVEN_REPO}"

docker run --rm -u "$(id -u):$(id -g)" \
  -v "${REPO_ROOT}:/work" \
  -v "${MAVEN_REPO}:/m2" \
  -w /work -e HOME=/tmp \
  "${MAVEN_IMAGE}" \
  mvn -q -Dmaven.repo.local=/m2 "$@" package

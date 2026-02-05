#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

DATA_DIR="${DATA_DIR:-${ROOT_DIR}/data}"
IMAGE_NAME="${IMAGE_NAME:-promptrecorder-server}"
IMAGE_TAG="${IMAGE_TAG:-local}"
SERVER_PORT="${SERVER_PORT:-8080}"

mkdir -p "${DATA_DIR}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to run the service." >&2
  exit 1
fi

COMPOSE_CMD="docker compose"
if ! ${COMPOSE_CMD} version >/dev/null 2>&1; then
  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD="docker-compose"
  else
    echo "docker compose (v2) or docker-compose (v1) is required." >&2
    exit 1
  fi
fi

export DATA_DIR IMAGE_NAME IMAGE_TAG SERVER_PORT

${COMPOSE_CMD} -f "${ROOT_DIR}/docker-compose.yml" up -d

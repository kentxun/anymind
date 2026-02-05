#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

IMAGE_NAME="${IMAGE_NAME:-promptrecorder-server}"
IMAGE_TAG="${IMAGE_TAG:-local}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to build the image." >&2
  exit 1
fi

echo "Building Docker image ${IMAGE_NAME}:${IMAGE_TAG}..."
docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" "${ROOT_DIR}"

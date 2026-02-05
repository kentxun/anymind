#!/usr/bin/env bash
set -euo pipefail

# ---- configurable ----
REPO_URL="https://example.com/your/repo.git"
REPO_REF="main"
REPO_TOKEN="" # optional: https token for private repo
APP_DIR="/opt/promptrecorder"
SERVER_DIR="${APP_DIR}/Server"
SERVER_PORT="8080"
DATA_DIR="/var/lib/promptrecorder/data"
# ----------------------

log() {
  echo "[cloud-init] $*"
}

install_pkgs() {
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update
    apt-get install -y git ca-certificates curl
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y git ca-certificates curl
  elif command -v yum >/dev/null 2>&1; then
    yum install -y git ca-certificates curl
  else
    log "No supported package manager found (apt/dnf/yum)."
    exit 1
  fi
}

install_docker() {
  if command -v docker >/dev/null 2>&1; then
    return
  fi
  curl -fsSL https://get.docker.com | sh
  systemctl enable --now docker
}

ensure_compose() {
  if docker compose version >/dev/null 2>&1; then
    return
  fi

  if command -v apt-get >/dev/null 2>&1; then
    apt-get install -y docker-compose-plugin
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y docker-compose-plugin
  elif command -v yum >/dev/null 2>&1; then
    yum install -y docker-compose-plugin
  else
    log "Cannot install docker compose plugin."
    exit 1
  fi
}

clone_repo() {
  local repo_url="${REPO_URL}"
  if [ -n "${REPO_TOKEN}" ] && [[ "${REPO_URL}" == https://* ]]; then
    repo_url="https://${REPO_TOKEN}@${REPO_URL#https://}"
  fi

  if [ -d "${APP_DIR}/.git" ]; then
    log "Updating repo..."
    git -C "${APP_DIR}" fetch --all --prune
    git -C "${APP_DIR}" checkout "${REPO_REF}"
    git -C "${APP_DIR}" reset --hard "origin/${REPO_REF}"
  else
    log "Cloning repo..."
    mkdir -p "${APP_DIR}"
    git clone --depth 1 --branch "${REPO_REF}" "${repo_url}" "${APP_DIR}"
  fi
}

main() {
  install_pkgs
  install_docker
  ensure_compose
  clone_repo

  log "Deploying server..."
  export SERVER_PORT DATA_DIR
  "${SERVER_DIR}/scripts/deploy.sh"
  log "Done. Health check: http://<public-ip>:${SERVER_PORT}/health"
}

main "$@"

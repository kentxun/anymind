# Aliyun ECS One-Click Deploy (Docker)

This uses ECS + cloud-init user data to provision Docker and run the server.
It builds the Docker image on the ECS host from this repo.

## 1) Prepare repo access
If the repo is private, create a read-only token or deploy key.
You will paste it into `REPO_TOKEN` in the user-data script (see below).

## 2) Create ECS instance
Recommended:
- OS: Ubuntu 22.04 LTS or Alibaba Cloud Linux 3
- Public IP: enabled
- Security group: allow inbound TCP 8080 (or your custom port)
- User data: paste the script below (edit config first)

## 3) User data (cloud-init)
Copy `cloud-init.sh` and edit these variables:
- `REPO_URL` (your git URL)
- `REPO_REF` (branch/tag)
- `REPO_TOKEN` (optional)
- `SERVER_PORT` and `DATA_DIR` (optional)

Path: `Server/deploy/aliyun/cloud-init.sh`

## 4) Verify
After ECS is ready:
- `http://<public-ip>:8080/health`

## Notes
- Data is stored in `DATA_DIR` on the ECS host (default: `/var/lib/promptrecorder/data`).
- For HTTPS, add a reverse proxy (Nginx/Caddy) and point it to `localhost:8080`.
- If you want image build locally + push to ACR, I can add a second path.

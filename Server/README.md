# PromptRecorder Server (Java)

Lightweight sync-only server for PromptRecorder. Uses Spring Boot + SQLite.

## Requirements
- Java 8+
- Maven 3.8+

## Run
```bash
mvn spring-boot:run
```

Default config: `Server/src/main/resources/application.yml`
- Port: `8080`
- Storage root: `Server/data`

Override storage root:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--storage.root=/path/to/data"
```

## Docker / Cloud Deploy (Recommended)
Prereqs: Docker + docker compose.

Build image:
```bash
./scripts/build.sh
```

Run service:
```bash
./scripts/run.sh
```

One-click build + run:
```bash
./scripts/deploy.sh
```

Defaults:
- Port: `8080`
- Data dir (SQLite files): `Server/data` (mounted to `/data` in container)

Overrides (optional):
```bash
SERVER_PORT=18080 DATA_DIR=/mnt/promptrecorder-data ./scripts/run.sh
```

Aliyun ECS one-click:
- See `Server/deploy/aliyun/README.md`

## API
- `POST /spaces`
  - Create a new space (no auth)
- `POST /sync/push`
  - Push local changes (requires `space_id` + `space_secret`)
- `POST /sync/pull`
  - Pull remote changes (requires `space_id` + `space_secret`)
- `GET /health`
  - Health check

## Storage Layout
```
{storage.root}/
  registry.sqlite
  spaces/
    {spaceId}/
      space.sqlite
```

## Notes
- No login/registration; security via `space_id` + `space_secret`.
- Server does not perform search; clients search locally.
- Conflict strategy is LWW (server always accepts, returns conflict flag).

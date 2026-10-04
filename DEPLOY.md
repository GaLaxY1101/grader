# Deploying Grader on a server

One command starts the whole stack: PostgreSQL, Keycloak, S3 storage, GitLab CE + runner,
Ollama (LLM, model downloaded automatically), the sandbox images, the Spring Boot backend and
the Next.js frontend. One manual step remains after the first start: connecting GitLab
(step 5).

```
browser ──► frontend :3000 ──(server side)──► backend :8080 ──► postgres, s3, ollama
   │                                              │
   ├──► backend :8080 (API calls from the page)   ├──► docker.sock ──► sandbox containers
   ├──► keycloak :9080 (login page)               └──► gitlab :8929 ◄── gitlab-runner ──► CI job containers
   └──► s3 :9000 (file downloads)                          └── webhook ──► backend
```

## Requirements

- Linux server with Docker Engine 24+ and the Docker Compose plugin (`docker compose version`).
- Resources: 4+ CPU cores, 12 GB RAM, 30 GB disk. Measured idle footprint: GitLab ~2.8 GB,
  Ollama ~2 GB with the model loaded, backend ~0.6 GB, Keycloak ~0.4 GB, the rest < 0.3 GB.
- **Outbound internet** on the server: images and the LLM model are pulled at first start, and
  every CI job pulls `python:3.12-slim` / `gcc` and runs `pip install pytest`.
- **Outbound internet in users' browsers**: the code editor (Monaco) loads from
  `cdn.jsdelivr.net`. On an offline network the editor stays blank.
- Firewall: open 3000, 8080, 9080, 9000, 8929 (and 2224 for git over SSH, optional).
- Optional NVIDIA GPU with the driver and `nvidia-container-toolkit`; AI test generation is
  roughly 10× faster.

## Steps

1. **Get the code.** The frontend lives in a separate repository, and the compose file builds
   it from `../grader-frontend`, so clone both side by side:
   ```bash
   mkdir grader && cd grader
   git clone <backend-repo-url>  grader
   git clone <frontend-repo-url> grader-frontend
   cd grader
   ```

2. **Configure**
   ```bash
   cp .env.example .env
   ```
   - `PUBLIC_HOST`: hostname or IP that **browsers** use to reach the server (no scheme, no port).
     It ends up in token issuers, redirect URIs, CORS and the frontend bundle, so pick the
     final name now (see "Changing PUBLIC_HOST later").
   - Replace every `change-me`. Generate values with `openssl rand -hex 32`. Hex avoids
     characters like `/`, `+` and `=`, which break the sed/URL edits some people do later.
   - Leave `GITLAB_TOKEN` empty for now.

3. **Start**
   ```bash
   docker compose -f compose.server.yaml up -d --build
   ```
   With a GPU:
   ```bash
   docker compose -f compose.server.yaml -f compose.gpu.yaml up -d --build
   ```
   The first start takes 10–20 minutes. Maven and pnpm download dependencies, the LLM model
   (~2 GB) downloads, and GitLab initializes (~5 min). The backend starts only after Postgres,
   Keycloak, the model and the sandbox images are ready, and the frontend starts after Keycloak.
   `docker compose ps` shows `sandbox-cpp`, `sandbox-python` and `ollama-pull` as
   `Exited (0)`. That is expected: they only build or download things.

4. **Check**
   ```bash
   curl http://localhost:8080/actuator/health        # {"status":"UP"}
   ```
   Open `http://PUBLIC_HOST:3000` and sign in with a demo account from
   `keycloak/realm-export.json`, e.g. `teacher@grader.ua` / `Teacher123!`. Change or remove the
   demo accounts before real use (Keycloak admin console → realm `university-grader` → Users).

5. **Connect GitLab (once).** Grading student submissions through CI needs this step.
   Wait until `docker compose -f compose.server.yaml ps gitlab` shows `healthy`.

   a. Create an API token for `root` (scopes `api`, `create_runner`):
   ```bash
   TOKEN=glpat-$(openssl rand -hex 13)
   docker compose -f compose.server.yaml exec gitlab gitlab-rails runner \
     "t = User.find_by_username('root').personal_access_tokens.create!(name: 'grader', scopes: ['api','create_runner'], expires_at: 365.days.from_now); t.set_token('$TOKEN'); t.save!"
   echo $TOKEN
   ```
   This takes about a minute. To use the UI instead, open `http://PUBLIC_HOST:8929` and sign in
   as `root`. The password is in `docker compose -f compose.server.yaml exec gitlab cat /etc/gitlab/initial_root_password`,
   and the file is deleted after 24 h. Then go to User settings → Access tokens.

   b. Put `TOKEN` into `.env` as `GITLAB_TOKEN`, then start the backend so it creates the
   `grader` group:
   ```bash
   docker compose -f compose.server.yaml up -d backend
   ```
   The backend log should end with `GitLab ready — group 'grader' id=<N>`. Capture that ID —
   step c needs it. If you missed the log line, grab it from the API:
   ```bash
   GROUP_ID=$(curl -s -H "PRIVATE-TOKEN: $TOKEN" \
     "http://localhost:8929/api/v4/groups?search=grader" \
     | sed 's/.*"id":\([0-9]*\),"web_url".*/\1/')
   ```

   c. Register two **group** runners scoped to `grader` (adjust the loop count to run more
   graded submissions in parallel):
   ```bash
   for i in 1 2; do
     RUNNER_TOKEN=$(curl -s -X POST -H "PRIVATE-TOKEN: $TOKEN" \
       http://localhost:8929/api/v4/user/runners \
       -d runner_type=group_type -d "group_id=$GROUP_ID" \
       -d run_untagged=true -d "description=grader-group-$i" \
       | sed 's/.*"token":"\([^"]*\)".*/\1/')

     docker compose -f compose.server.yaml exec gitlab-runner gitlab-runner register \
       --non-interactive --url http://gitlab:8929 --clone-url http://gitlab:8929 \
       --token "$RUNNER_TOKEN" --executor docker --docker-image alpine:latest \
       --docker-network-mode grader-server_default \
       --description "grader-group-$i"
   done
   ```
   `--clone-url` is required. Without it, CI jobs clone from GitLab's external URL, and inside
   the job container that URL may not resolve: with `PUBLIC_HOST=localhost` it points at the
   job container itself (`Failed to connect to localhost port 8929`).

   d. Raise the runner process's concurrency cap so both runners actually grade in parallel.
   The top-level `concurrent` key in `/etc/gitlab-runner/config.toml` caps simultaneous jobs
   across **all** registered runners (default `1`); set it `>=` the number of runners:
   ```bash
   docker compose -f compose.server.yaml exec gitlab-runner \
     sed -i 's/^concurrent = .*/concurrent = 2/' /etc/gitlab-runner/config.toml
   docker compose -f compose.server.yaml restart gitlab-runner
   ```
   Verify with `docker compose -f compose.server.yaml exec gitlab-runner gitlab-runner list`:
   two runners should appear, both with `URL=http://gitlab:8929`.

   To add another runner later, repeat step c with a fresh `i` and bump `concurrent` to match.

6. **Configure SMTP (optional, re-run after changing `.env`).** The realm JSON imports SMTP
   host/user/from on first boot only, and never ships the SMTP password in plaintext. The
   `keycloak-config` one-shot pushes the password into the live realm and sets an email on the
   master-realm `admin` user so Keycloak's *Test connection* button has a recipient. Set
   `KEYCLOAK_ADMIN_EMAIL` and `SMTP_PASSWORD` in `.env`, then run it manually:
   ```bash
   docker compose -f compose.server.yaml run --rm keycloak-config
   ```
   Each step is skipped when its variable is empty, so this is safe to re-run (for example after
   rotating the SMTP password). Verify in Keycloak admin console → realm `university-grader` →
   Realm settings → Email → *Test connection*.

7. **Smoke test.** As a teacher, create a Python assignment with *Enable Code Check* and a
   reference solution plus tests. As `student@grader.ua`, submit a solution. Within about a
   minute the attempt should show `Passed` with per-test results.

## Operations

| Task | Command |
|---|---|
| Status | `docker compose -f compose.server.yaml ps` |
| Logs | `docker compose -f compose.server.yaml logs -f backend` (or `frontend`, `keycloak`, …) |
| Update after `git pull` (both repos) | `docker compose -f compose.server.yaml up -d --build` |
| Stop | `docker compose -f compose.server.yaml down` (data volumes are kept) |
| Change LLM model | set `TESTGEN_MODEL` in `.env`, then `up -d` (the model is pulled automatically) |
| Backup DB | `docker compose -f compose.server.yaml exec postgres pg_dump -U grader grader > grader.sql` |
| Wipe everything | `docker compose -f compose.server.yaml down -v` (**deletes all data**, including GitLab) |

`up -d --build <service>` also restarts the services it depends on, so expect a short backend
restart when you rebuild the frontend.

## Gotchas

- **Keycloak imports the realm only once.** `keycloak/realm-export.json` and the
  `GRADER_FRONTEND_URL` / `GRADER_FRONTEND_CLIENT_SECRET` placeholders in it are applied only
  when the realm does not exist yet, on an empty Postgres volume. Later edits to the file or
  `.env` have no effect; change the client in the admin console (`http://PUBLIC_HOST:9080`,
  user `admin`, password `KEYCLOAK_ADMIN_PASSWORD`) instead.
- **Changing PUBLIC_HOST later** touches several places:
  1. Rebuild the frontend (`up -d --build frontend`). The API URL is compiled into the browser
     bundle.
  2. In Keycloak, add the new URL to the `grader-frontend` client's redirect URIs and web
     origins.
  3. In GitLab, change `external_url`; it comes from the compose file on container recreate.
- **Postgres passwords are set once.** Changing `POSTGRES_PASSWORD` in `.env` after the first
  start breaks the login, because the volume keeps the old password. Change it inside Postgres
  (`ALTER USER grader PASSWORD '...'`) first.
- **Windows checkouts.** With `core.autocrlf=true`, files get CRLF line endings. The frontend
  image skips lint for that reason. Keep `deploy/postgres/init-keycloak-db.sh` in LF (enforced
  by `.gitattributes`), or Postgres fails to run it.
- **AI test generation on CPU.** One LLM call on a 3b model takes about 1 minute on 16 cores,
  and a full generation runs several calls. The per-call limit is `testgen.ollama.timeout-seconds`
  (180). On a slow CPU, expect timeouts: use a GPU, or keep `qwen2.5-coder:3b`.
- **Port clash with the dev stack.** `compose.yaml` (dev) and `compose.server.yaml` publish the
  same ports. Stop one before starting the other. Their project names differ (`grader` vs
  `grader-server`), so their volumes do not mix.

## How code is compiled and run

- **Compile checks** (teacher saving an assignment, student "check" before submitting) and
  **AI test generation** run in short-lived containers from `grader-sandbox-cpp:1` /
  `grader-sandbox-py:1`, built by the `sandbox-*` services. The backend starts them on the host
  Docker daemon through `/var/run/docker.sock`, with no network, a read-only root filesystem, an
  unprivileged user, and CPU/memory/process limits. Source files are streamed in over stdin, so
  no host directory is shared. The compiler never runs inside the backend container, so
  `#include "/proc/self/environ"` cannot leak backend secrets.
- **Graded attempts** run as GitLab CI jobs on `gitlab-runner` (Docker executor). The backend
  pushes the code to a per-student project, and GitLab reports the result back through a
  webhook to `http://backend:8080/api/webhooks/gitlab`.

## Security

- **Docker socket:** mounting `/var/run/docker.sock` gives the backend and the runner
  root-equivalent control over the host's Docker. Keep the host dedicated to Grader and do not
  expose the Docker API over TCP.
- **Ports:** 3000 (frontend), 8080 (backend), 9080 (Keycloak), 9000 (file downloads), 8929/2224
  (GitLab) are published. PostgreSQL and Ollama are reachable only inside the compose network.
- **HTTPS:** not configured. Put a reverse proxy (nginx, Caddy, or the university's) with TLS in
  front before real use, then switch Keycloak from `start-dev` to `start` and update every
  `http://` URL derived from `PUBLIC_HOST` in `compose.server.yaml`, then test login again.
- **Demo accounts and secrets:** the realm export ships demo users with known passwords and a
  default frontend client secret (used only when `GRADER_FRONTEND_CLIENT_SECRET` is unset).
  Disable the demo users on a public server.
- **Swagger UI** (`/swagger-ui.html`) is public; it only describes the API, every call still
  needs a token.
- **Storage:** `adobe/s3mock` keeps files in a Docker volume. It is fine for a pilot; for long-term
  use replace it with MinIO.

## Not covered yet

- Reverse proxy serving frontend, API and Keycloak under one HTTPS origin.
- Self-hosting the Monaco editor assets for offline networks.
- Backups of GitLab (`gitlab-backup create`) and the S3 volume.

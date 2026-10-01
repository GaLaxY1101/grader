# Deploying Grader on a server

One command starts the whole backend stack: PostgreSQL, Keycloak, S3 storage, GitLab CE +
runner, Ollama (LLM, model downloaded automatically), the AI test sandbox images and the
Spring Boot backend.

The frontend is not containerized yet; see "Not covered yet" below.

## Requirements

- Linux server with Docker Engine 24+ and the Docker Compose plugin (`docker compose version`).
- Resources, roughly: 4 CPU cores, 12 GB RAM (GitLab ~4 GB, Ollama ~3 GB on CPU), 30 GB disk.
- Optional NVIDIA GPU with the driver and `nvidia-container-toolkit` installed; makes AI test
  generation roughly 10× faster.

## Steps

1. **Get the code**
   ```bash
   git clone <repo-url> grader && cd grader/grader
   ```

2. **Configure**
   ```bash
   cp .env.example .env
   ```
   Set `PUBLIC_HOST` to the hostname/IP browsers use, and replace every `change-me`
   (e.g. `openssl rand -base64 32`).

3. **Start**
   ```bash
   docker compose -f compose.server.yaml up -d --build
   ```
   With a GPU:
   ```bash
   docker compose -f compose.server.yaml -f compose.gpu.yaml up -d --build
   ```
   The first start takes a while: images build, the LLM model (~2 GB) downloads, GitLab
   initializes (~5 min). The backend starts only after its dependencies are ready.
   Check: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.

4. **Connect GitLab (once)** — needed for grading student submissions via CI:
   - Open `http://PUBLIC_HOST:8929`, sign in as `root`
     (password: `docker compose -f compose.server.yaml exec gitlab cat /etc/gitlab/initial_root_password`).
   - Create a personal access token with `api` scope → put it into `.env` as `GITLAB_TOKEN`.
   - Admin → CI/CD → Runners → New instance runner → copy the token into `.env` as
     `GITLAB_RUNNER_TOKEN`, then register the runner:
     ```bash
     docker compose -f compose.server.yaml exec gitlab-runner gitlab-runner register \
       --non-interactive --url http://gitlab:8929 --token <GITLAB_RUNNER_TOKEN> \
       --executor docker --docker-image alpine:latest --docker-network-mode grader-server_default
     ```
   - Apply: `docker compose -f compose.server.yaml up -d backend`.

## Operations

| Task | Command |
|---|---|
| Logs | `docker compose -f compose.server.yaml logs -f backend` |
| Update after `git pull` | `docker compose -f compose.server.yaml up -d --build` |
| Stop | `docker compose -f compose.server.yaml down` (data volumes are kept) |
| Change LLM model | set `TESTGEN_MODEL` in `.env`, then `up -d` (the model is pulled automatically) |
| Backup DB | `docker compose -f compose.server.yaml exec postgres pg_dump -U grader grader > grader.sql` |

## How AI test sandboxes run

The backend container has the Docker CLI and the host's `/var/run/docker.sock`. For every test
run it starts a short-lived container from `grader-sandbox-cpp:1` / `grader-sandbox-py:1`
(built by the `sandbox-*` services in the compose file) with no network, a read-only root
filesystem, an unprivileged user, and CPU/memory/process limits. Source files are streamed in
over stdin, so no host directory is shared.

## Security

- **Docker socket:** mounting `/var/run/docker.sock` gives the backend container root-equivalent
  control over the host's Docker. GitLab Runner already requires the same. Keep the host
  dedicated to Grader and do not expose the Docker API over TCP.
- **Ports:** only 8080 (backend), 9080 (Keycloak), 9000 (file downloads), 8929/2224 (GitLab) are
  published. PostgreSQL and Ollama are reachable only inside the compose network.
- **HTTPS:** not configured. Put a reverse proxy (nginx, Caddy, or the university's) with TLS in
  front before real use, and switch Keycloak from `start-dev` to `start` with proper hostname
  settings.
- **Storage:** `adobe/s3mock` keeps files in a Docker volume. It is fine for a pilot; for long-term
  use replace it with MinIO.

## Not covered yet

- Frontend container (Next.js) and a reverse proxy serving frontend + API under one origin.
- Keycloak realm redirect URIs point to `http://localhost:3000`; add the server's frontend URL
  in the Keycloak admin console (realm `university-grader` → Clients).

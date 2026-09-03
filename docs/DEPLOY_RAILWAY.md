# Deploying Riot Analizer to Railway

This is a monorepo (a Spring Boot backend, a Vite/React frontend served by
nginx, and Postgres). Railway builds **one app per service**, so we deploy it
as **three services in one Railway project**:

| Service    | Root directory | Builder    | Public? |
|------------|----------------|------------|---------|
| `backend`  | `backend`      | Dockerfile | yes (proxied by the frontend) |
| `frontend` | `frontend`     | Dockerfile | yes (this is your app URL)     |
| `Postgres` | —              | Railway DB | no      |

> **Why the `railpack could not determine how to build the app` error happened:**
> Railway was pointed at the repo **root**, which has no single language — just
> `backend/` and `frontend/` subfolders. The fix is to give each service its own
> **root directory** so Railway finds that folder's `Dockerfile`. The
> `railway.json` files in `backend/` and `frontend/` also force the Dockerfile
> builder, so Railpack is skipped entirely.

---

## 1. Create the project and add Postgres

1. Railway → **New Project** → **Deploy from GitHub repo** → pick
   `SebastianRabiej/Riot-Analizer`.
2. Railway creates one service from the repo. We'll turn it into the backend in
   step 2 (or delete it and add two fresh services — either works).
3. **New** → **Database** → **Add PostgreSQL**. No configuration needed.
   The service is named **Postgres** (used in the variable references below —
   adjust the names if yours differs).

## 2. Backend service

- **Settings → Source → Root Directory:** `backend`
- **Settings → Networking → Public Networking → Generate Domain.**
  Copy the domain, e.g. `backend-production-1a2b.up.railway.app` — the frontend
  needs it. (Target port: leave blank; the app listens on `$PORT`.)
- **Variables** (Postgres references resolve automatically inside the same
  project):

  ```
  SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
  SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
  SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
  RIOT_API_KEY=<your Riot API key>
  ```

  Optional (defaults exist in `application.yml`): `RIOT_INITIAL_PLAYER`,
  `RIOT_CURRENT_SEASON_START_EPOCH_MS`, `RIOT_FETCH_INTERVAL_MS`,
  `RIOT_FETCH_BATCH_SIZE`, `RIOT_MAX_HISTORY_MATCHES`, `LOG_LEVEL`.

  Do **not** set `PORT` or `SERVER_PORT` — Railway injects `PORT` and the app
  now binds it (`server.port=${PORT:8080}`).

- Health check is preconfigured to `/actuator/health` (in `backend/railway.json`).

## 3. Frontend service

- **New → GitHub Repo →** same repo (a second service from the same repo).
- **Settings → Source → Root Directory:** `frontend`
- **Settings → Networking → Generate Domain** → this domain is your app URL.
- **Variables** — point nginx at the backend's public domain from step 2:

  ```
  BACKEND_URL=https://backend-production-1a2b.up.railway.app
  BACKEND_HOST=backend-production-1a2b.up.railway.app
  ```

  (`BACKEND_URL` includes `https://`; `BACKEND_HOST` is the bare hostname used
  for the upstream `Host` header / SNI.) `PORT` is injected by Railway and nginx
  binds it automatically.

## 4. Deploy

Push to `main` (or click **Deploy**). Order doesn't matter — Railway restarts
dependents. Open the frontend domain. Requests to `/api/*` and `/actuator/*`
are proxied server-side to the backend, so the browser only ever talks to the
frontend origin (no CORS involved).

---

## Notes & gotchas

- **Riot dev keys expire every 24h.** The backend will start but fetch nothing
  once the key lapses — refresh `RIOT_API_KEY` on the backend service (or apply
  for a production key at https://developer.riotgames.com/).
- **First backend boot** creates the schema (`ddl-auto: update`). The Postgres
  volume persists across redeploys.
- **Cost / exposure — keeping the backend private (optional hardening).**
  The setup above exposes the backend publicly (simplest, most robust). To keep
  it private instead, remove the backend's public domain and set the frontend to
  reach it over Railway's private network:

  ```
  BACKEND_URL=http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:${{backend.PORT}}
  BACKEND_HOST=${{backend.RAILWAY_PRIVATE_DOMAIN}}
  ```

  Trade-off: nginx resolves the upstream once at startup, so if the backend
  redeploys and gets a new internal address you may need to restart the frontend
  service. For a first deploy, the public backend is easier to reason about.

## Local development is unchanged

`docker compose up --build` (or `make up`) still works — the frontend
Dockerfile bakes local defaults (`PORT=80`, `BACKEND_URL=http://backend:8080`,
`BACKEND_HOST=backend`), so the templated nginx behaves exactly like before.
Open http://localhost:50398.

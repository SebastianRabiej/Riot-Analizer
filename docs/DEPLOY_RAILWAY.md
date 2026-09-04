# Deploying Riot Analizer to Railway (Infrastructure as Code)

The entire environment is defined in code and recreated with one command — no
hardcoded URLs, no manual dashboard wiring.

- `.railway/railway.ts` — declares the three services (backend, frontend,
  Postgres), their build config, health check, pinned ports, and all
  environment variables. The frontend finds the backend through a Railway
  reference variable (`${{backend.RAILWAY_PUBLIC_DOMAIN}}`), so nothing is
  hardcoded.
- `scripts/railway-provision.sh` — applies the IaC, generates the two public
  domains (Railway IaC cannot declare generated `*.up.railway.app` domains, so
  the CLI does it), and redeploys the frontend to pick up the backend's domain.

## One-time setup

```bash
npm i -g @railway/cli
railway login
railway link          # create/select the Railway project + environment
export RIOT_API_KEY=your-riot-dev-key
```

## Recreate the whole environment

```bash
make railway-provision
# or: RIOT_API_KEY=xxxxx ./scripts/railway-provision.sh
```

That script runs, in order:

1. `railway config apply --yes` — creates/updates the backend, frontend and
   Postgres services and all their variables from `.railway/railway.ts`.
2. `railway domain --service backend --port 8080` — backend public domain.
3. `railway domain --service frontend --port 80` — frontend public domain (your
   app URL).
4. `railway redeploy --service frontend` — re-renders the frontend's nginx
   config now that the backend domain exists.

When it finishes, open the printed frontend URL.

## How the wiring works (why nothing is hardcoded)

- **Ports are pinned** in `.railway/railway.ts` (`PORT: "8080"` backend,
  `PORT: "80"` frontend) so the generated domains target the right port
  deterministically, and the provision script is non-interactive.
- **Backend ← Postgres**: `SPRING_DATASOURCE_URL` etc. reference
  `${{Postgres.PGHOST}}`, `${{Postgres.PGPORT}}`, `${{Postgres.PGDATABASE}}`,
  `${{Postgres.PGUSER}}`, `${{Postgres.PGPASSWORD}}`.
- **Frontend ← Backend**: `BACKEND_URL` / `BACKEND_HOST` reference
  `${{backend.RAILWAY_PUBLIC_DOMAIN}}`, which resolves once step 2 has generated
  the backend's domain. The templated nginx (`frontend/nginx.conf.template`)
  resolves the upstream at request time, so the frontend still boots and serves
  the static site even before that (API calls 502 until the backend domain
  exists).
- **Secret**: `RIOT_API_KEY` is read from the environment at apply time
  (`process.env.RIOT_API_KEY` in the IaC), so it never lives in git.

## Notes

- **Riot dev keys expire every 24h.** Refresh with
  `railway variables --service backend --set RIOT_API_KEY=<new>` (or re-run the
  provision with a fresh `RIOT_API_KEY`).
- **First boot** creates the DB schema (`ddl-auto: update`). The Postgres volume
  persists across redeploys.
- **The `(verify)` lines in `railway.ts`** (`builder: "dockerfile"`) are the
  only spots that may need a tweak if `railway config plan` flags them — the
  IaC DSL is new. Run `railway config plan` to preview before applying.
- The per-service `railway.json` / `frontend/railway.json` files are the older,
  deprecated per-service config and are superseded by `.railway/railway.ts`.

## Local development is unchanged

`docker compose up --build` (or `make up`) still works — the frontend image
bakes local defaults, so the templated nginx behaves as before. Open
http://localhost:50398.

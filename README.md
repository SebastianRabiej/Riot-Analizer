# Riot Analizer

Riot Analizer is a League of Legends stats app built around a single tracked player (and designed to extend to many). It continuously pulls match and ranked data from the Riot Games API, stores it in PostgreSQL, and serves it to a React single-page app that shows season stats, per-champion breakdowns, recent form, live-game info, and head-to-head comparisons against other players or against specific enemy champions. On top of the raw stats it surfaces **auto-generated insights** (nemesis, best duo, bogey pick, strongest role, streaks and tilt), **teammate/duo synergy** (who you actually win *with*), a **time & tilt** breakdown (win rate by hour, weekday and game-number-in-a-session), and **role & lane-opponent** analysis (your record vs the enemy in your own position). Every insight is clickable and drills into the detail behind it. Only **current-season** matches are counted toward stats.

## Architecture

The system is three containers wired together by Docker Compose:

- **backend** — a Spring Boot 3 / Java 21 service. It exposes a REST API under `/api` and Spring Boot Actuator under `/actuator`, and listens on container port **8080**. It owns three interesting pieces of machinery: a **scheduled fetcher** that periodically refreshes each tracked player (account, summoner, rank, new match ids, and full match details for all 10 participants of every match), a **shared rate limiter** that every outbound Riot call passes through so the app never exceeds the dev-key limits, and **current-season filtering** — because match-v5 no longer returns a season id, "current season" is defined as any match whose `gameCreation` timestamp is at or after a configured epoch value.
- **frontend** — a React + Vite + TypeScript SPA, built to static files and served by **nginx**. In production nginx also reverse-proxies `/api` and `/actuator` back to the backend, so the browser talks to a single origin and there are no CORS concerns. Nginx listens on container port **80**.
- **db** — PostgreSQL 16. It stores every participant of every fetched match (all 10), which is what makes the head-to-head and vs-champion features possible. Data lives in a named volume (`pgdata`) so it survives container restarts.

The tracked-player model, storing all participants, means you can ask questions like "how do I do when the enemy team has Yasuo?" or "what's my record in games where this specific friend was also playing?".

## Prerequisites

You need **Docker** and the **Docker Compose plugin** (`docker compose ...`, v2 syntax). That's it for running the app — the backend and frontend are built inside multi-stage Docker images, so you do not need Java, Maven, Node, or npm installed locally unless you want to do non-Docker local development (see below).

You also need a **Riot developer API key**.

## Getting a Riot API key

Go to <https://developer.riotgames.com/>, sign in with your Riot account, and copy the **Development API Key** shown on the dashboard. Paste it into your `.env` (see below).

Two important caveats:

1. **The development key expires every 24 hours.** When fetches suddenly start failing with auth errors, the key has almost certainly rotated. Grab a fresh key from the developer portal, update `RIOT_API_KEY` in `.env`, and restart the backend (`docker compose up -d --build backend`, or just `make rebuild`). For anything long-lived you'd register a Personal or Production key instead.
2. **Region is EUNE.** This app is wired for Europe Nordic & East: the platform host is `eun1.api.riotgames.com` and the regional (account/match) host is `europe.api.riotgames.com`. Players are addressed by Riot ID — a `gameName` plus a `tagLine` (for example `Faker` / `KR1`) — not by the old summoner name.

## Configuration (`.env`)

Copy the example file and edit it:

```bash
cp .env.example .env
```

Then set at minimum `RIOT_API_KEY` (required — nothing works without it). The other values have sensible defaults:

- `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` — database credentials. The backend's JDBC URL is assembled from these automatically as `jdbc:postgresql://db:5432/<POSTGRES_DB>`.
- `RIOT_CURRENT_SEASON_START_EPOCH_MS` — epoch milliseconds marking the start of the current season (default `1736380800000`, i.e. 2025-01-09). Bump this when a new season begins.
- `RIOT_INITIAL_PLAYER` — optionally a `gameName#tagLine` to auto-track on startup. Leave it blank to add players through the UI instead.
- `RIOT_FETCH_INTERVAL_MS` — how often the scheduler runs, in milliseconds (default `600000`, i.e. 10 minutes).

`.env` is git-ignored — keep your key out of version control.

## Running with Docker Compose

From the repository root:

```bash
docker compose up --build
```

(or `make up` to build and run detached in the background). This builds all three images, waits for the database to become healthy, starts the backend, and finally starts the nginx frontend.

### Finding the frontend port

The frontend is deliberately published on a **random host port** to avoid conflicts with anything else on your machine — Compose gives Docker only the container port (`80`) and lets it pick an ephemeral host port. To find out which one you got:

```bash
docker compose port frontend 80
```

This prints something like `0.0.0.0:49173`; open `http://localhost:49173` in your browser. You can also run `make port` for the same thing, or `docker compose ps` to see the full port mapping for every service. (The backend is likewise published on a random host port for debugging only — normal traffic reaches it through the nginx proxy, not directly.)

## Setting the tracked player

There are three ways to tell the app whom to track:

1. Set `RIOT_INITIAL_PLAYER=gameName#tagLine` in `.env` before starting, and the backend registers and begins tracking that player on startup.
2. Use the **search box in the UI** — looking up a Riot ID resolves it against Riot, registers the player as tracked, and returns their profile. (`GET /api/players/{gameName}/{tagLine}`.)
3. Trigger an immediate refresh for a player with a POST: `POST /api/players/{gameName}/{tagLine}/refresh`, which does a rate-limited fetch right away instead of waiting for the next scheduled run.

The list of tracked players is available at `GET /api/tracked`.

## Rate limiting and the scheduler

Riot development keys are capped at **20 requests per second** and **100 requests per 120 seconds**, and those limits apply across *all* calls the app makes. The backend enforces this with a shared limiter built from two token buckets; every outbound Riot request must acquire a token from both buckets first, blocking until tokens are available. This means the scheduler can never burst past the limit — it simply waits.

The scheduler itself runs on a fixed delay (`RIOT_FETCH_INTERVAL_MS`, default 10 minutes). On each run, for every tracked player it refreshes account/summoner/rank data, asks for the newest match ids since the last one it stored, fetches and persists any missing match details (all 10 participants each), and refreshes the live-game cache — all through the rate limiter. Because dev-key throughput is modest, the first fill of a fresh database can take a while; subsequent runs only fetch the delta.

## Local development without Docker

For a tighter feedback loop you can run the two halves directly, pointing at a local Postgres (or the Dockerized `db` service with its port published).

Backend:

```bash
cd backend
# export the same env vars from your .env (RIOT_API_KEY, SPRING_DATASOURCE_URL, etc.)
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server runs on `http://localhost:5173` and proxies `/api` to `http://localhost:8080` (configured in `vite.config.ts`), so the frontend reaches your locally running backend the same way it reaches the proxied backend in production. Remember the dev-key expiry: if backend calls start failing after a day, refresh `RIOT_API_KEY`.

## Handy Make targets

- `make up` — build and start everything detached.
- `make down` — stop and remove the containers (the `pgdata` volume is kept).
- `make logs` — tail logs from all services.
- `make port` — print the frontend's random host port.
- `make rebuild` — rebuild images and force-recreate the containers.

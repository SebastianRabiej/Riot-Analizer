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
- `RIOT_SCHEDULER_TICK_MS` — how often the scheduler wakes up to look for players that are due (default `15000`). Each player is fetched when its own `next_check_at` is due, or earlier while the API budget has spare room (see below).
- `RIOT_EARLY_CHECK_*` — early checks with spare budget (see "Early checks" below): `ENABLED` (default `true`), `TRACKED_MIN_GAP_MS` / `DISCOVERED_MIN_GAP_MS` (defaults `120000` / `1800000`), `TRACKED_MIN_FREE_RATIO` / `DISCOVERED_MIN_FREE_RATIO` (defaults `0.4` / `0.6`).
- `RIOT_DISCOVERY_MAX_DEPTH` — how far discovery spreads from tracked players (default `1`; `-1` = unbounded, `0` = off). `RIOT_DISCOVERY_MAX_PLAYERS` caps the number of discovered players (default `0` = no cap).

`.env` is git-ignored — keep your key out of version control.

## Admin login (tracking is admin-only)

The stats pages are public, but **everything about tracking** belongs to a single admin account: the tracked-player list (home page + top bar), the *Manage tracking* page with discovered players, the Track/Untrack buttons, and the tracking state in player profiles. Visitors can still look up any Riot ID.

Set it up once:

```bash
make admin-hash            # or ./scripts/admin-hash.sh — asks for the password twice, prints a BCrypt hash
```

The script needs `htpasswd` (built into macOS; `apt install apache2-utils` on Debian/Ubuntu) or Python with `pip install bcrypt`. The password is read at a hidden prompt and never stored or passed as an argument.

and put the result in `.env` — **keep the single quotes**, the hash contains `$` characters that Docker Compose would otherwise try to expand:

```bash
ADMIN_USERNAME=admin
ADMIN_PASSWORD_HASH='$2y$12$...'
```

Then sign in via **🔒 Admin** in the top bar (`/login`). Use a long random password (16+ characters; BCrypt only looks at the first 72 bytes). If `ADMIN_PASSWORD_HASH` is empty, admin login is disabled; if it isn't a valid BCrypt hash the backend refuses to start.

How it is protected:

- **Storage** — only a BCrypt (cost 12) hash is configured via environment; the password is never stored in git, the database or logs.
- **Checking** — the hash is verified server-side with a constant-time username compare and a BCrypt check that always runs, so timing doesn't reveal which part was wrong. After `ADMIN_MAX_FAILED_LOGINS` (10) failures within `ADMIN_LOCKOUT_MINUTES` (15), login is blocked with HTTP 429 until the window passes.
- **Session** — a server-side session in an `HttpOnly`, `SameSite=Strict`, `Secure` cookie (`RA_SESSION`); the id is rotated at login and the session is invalidated at logout. Idle timeout `ADMIN_SESSION_TIMEOUT` (8h). Sessions are in memory, so restarting the backend logs you out.
- **CSRF** — every POST/PUT/DELETE (login and logout included) must echo the `XSRF-TOKEN` cookie in an `X-XSRF-TOKEN` header; the token is rotated at login.
- **Enforcement** — the backend is the gatekeeper: `/api/tracked`, `/api/discovered` and `POST`/`DELETE /api/players/{name}/{tag}/track` return 401 without the admin session (URL rules plus `@PreAuthorize`), and player profiles omit tracking fields for visitors. The UI hiding is cosmetic on top of that.

`APP_COOKIE_SECURE` defaults to `true`; `docker-compose.yml` sets it to `false` because the local stack is plain http. Keep it `true` anywhere served over HTTPS. Endpoints: `GET /api/auth/me`, `POST /api/auth/login` (`{"username","password"}`), `POST /api/auth/logout`.

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

There are two ways to tell the app whom to track:

1. Set `RIOT_INITIAL_PLAYER=gameName#tagLine` in `.env` before starting, and the backend registers and begins tracking that player on startup.
2. As the **admin**, use *Manage tracking* (`/tracked`) or the **☆ Track player** button on a player's page (`POST /api/players/{gameName}/{tagLine}/track`; `DELETE` untracks).

Anyone can look a player up with the search box (`GET /api/players/{gameName}/{tagLine}`) or force a refresh (`POST /api/players/{gameName}/{tagLine}/refresh`) — that fetches their profile but does not track them.

The list of tracked players is available to the admin at `GET /api/tracked`.

## Rate limiting and the scheduler

Riot development keys are capped at **20 requests per second** and **100 requests per 120 seconds**, and those limits apply across *all* calls the app makes. The backend enforces this with a shared limiter built from two token buckets; every outbound Riot request must acquire a token from both buckets first, blocking until tokens are available. This means the scheduler can never burst past the limit — it simply waits.

### Priorities

The limiter is priority-aware: when a permit frees up it goes to the most important waiter, in this order:

1. **REFRESH** — anything a user is waiting for (manual refresh, track, search, live lookup)
2. **ANALYSIS** — a match timeline someone opened
3. **TRACKED_HISTORY** — keeping tracked players' history fresh
4. **DISCOVERED_HISTORY** — history of players met in tracked players' games, spare budget only

Background priorities also leave part of the 2-minute window free (`RIOT_RATE_LIMIT_TRACKED_RESERVE`, `RIOT_RATE_LIMIT_DISCOVERED_RESERVE`) so user requests start immediately.

### Tracking levels and discovery

Every stored player has a `tracking_level`: `TRACKED` (chosen by a user), `DISCOVERED` (appeared in a stored match of a tracked player — or, with a higher `RIOT_DISCOVERY_MAX_DEPTH`, of another discovered player) or `NONE` (known but not fetched in the background). Discovered players get their current season backfilled too, but only after every tracked player is up to date, closest discovery depth first.

### Staleness: when is a player fetched?

The scheduler wakes every `RIOT_SCHEDULER_TICK_MS` and picks the most important player whose `next_check_at` is due (tracked first, then discovered), one at a time. A check costs as few calls as possible:

- **Backfill** (season not fully stored yet): walk the season's match ids newest-first, fetching up to `RIOT_FETCH_BATCH_SIZE` missing matches per check, until the whole season is stored (`backfill_complete`).
- **Head check** (afterwards): *one* match-id call since the newest known match. If nothing new comes back, the player is up to date.
- **Profile/rank** (2 calls) only after new games, or when older than 6 h (tracked) / 7 days (discovered).
- **Live game** (1 call) for tracked players only.

`next_check_at` then follows recent activity — a tracked player who played within the hour is checked every 5 minutes (4 while in a live game), one idle for weeks every 12 hours; discovered players every 2 h – 3 days (see `CheckCadence`).

### Early checks: using spare budget

That cadence is the floor for when the budget is scarce. When the long window is mostly idle, the scheduler also pulls players forward before their `next_check_at` (see `EarlyCheckPolicy`), in this order: due tracked → **early tracked** → due discovered → **early discovered**. An early check only happens while:

- at least `RIOT_EARLY_CHECK_TRACKED_MIN_FREE_RATIO` (default `0.4`, i.e. 40 of 100 on a dev key) of the long window is unused — `RIOT_EARLY_CHECK_DISCOVERED_MIN_FREE_RATIO` (default `0.6`) for discovered players, and
- the player's `early_check_at` has passed: at least `RIOT_EARLY_CHECK_TRACKED_MIN_GAP_MS` (default 2 min) / `RIOT_EARLY_CHECK_DISCOVERED_MIN_GAP_MS` (default 30 min) since their last check. Error and not-found back-offs are never cut short.

Soonest-scheduled players go first, so as other work fills the window early checks stop by themselves. `RIOT_EARLY_CHECK_ENABLED=false` turns them off. The Fetch Queue view marks early picks and shows whether early checks are currently running or paused.

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
- `make admin-hash` — prompt for an admin password and print its BCrypt hash.
- `make backend-test` — run the backend tests in a Maven container.

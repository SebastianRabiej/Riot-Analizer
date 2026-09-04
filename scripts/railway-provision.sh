#!/usr/bin/env bash
#
# Fully-scripted, reproducible Railway provision for Riot Analizer.
# Recreates the WHOLE environment from code: services + Postgres + variables
# (from .railway/railway.ts) and the two public domains (via the CLI, since
# Railway IaC cannot declare generated *.up.railway.app domains). Nothing is
# hardcoded — the frontend finds the backend through a ${{backend.*}} reference.
#
# Prerequisites (one-time):
#   - Railway CLI installed and logged in:      railway login
#   - A linked project + environment:           railway link
#   - A Riot dev API key exported in your shell: export RIOT_API_KEY=xxxxx
#
# Usage:
#   RIOT_API_KEY=xxxxx ./scripts/railway-provision.sh
#
set -euo pipefail

: "${RIOT_API_KEY:?Set RIOT_API_KEY before running (a Riot dev key from https://developer.riotgames.com/).}"

BACKEND_PORT=8080   # must match PORT pinned for the backend in .railway/railway.ts
FRONTEND_PORT=80    # must match PORT pinned for the frontend in .railway/railway.ts

echo "==> 1/4  Applying infrastructure from .railway/railway.ts (services, Postgres, variables)"
railway config apply --yes

echo "==> 2/4  Generating the backend's public domain (port ${BACKEND_PORT})"
# Idempotent: ignore the error if a domain already exists for this service.
railway domain --service backend --port "${BACKEND_PORT}" || true

echo "==> 3/4  Generating the frontend's public domain (port ${FRONTEND_PORT}) — this is your app URL"
railway domain --service frontend --port "${FRONTEND_PORT}" || true

echo "==> 4/4  Redeploying the frontend so nginx picks up the backend's now-existing domain"
# The frontend's BACKEND_URL is a reference to the backend's public domain, which
# only exists after step 2; a redeploy re-renders the nginx config with it.
railway redeploy --service frontend --yes || echo "   (redeploy skipped/failed — trigger a frontend redeploy in the dashboard if needed)"

echo
echo "==> Done. Frontend URL:"
railway domain --service frontend 2>/dev/null || railway status || true

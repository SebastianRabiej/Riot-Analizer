#!/usr/bin/env bash
#
# riot-analizer — one-command Docker launcher.
#
# Usage:
#   ./run.sh RGAPI-your-dev-key            # set key + build + start
#   ./run.sh RGAPI-your-key "Name#EUNE"    # also auto-track a player on startup
#   ./run.sh                               # reuse the key already in .env
#
set -euo pipefail
cd "$(dirname "$0")"

# 1. Ensure a .env exists (copied from the template on first run).
if [ ! -f .env ]; then
  cp .env.example .env
  echo "• Created .env from .env.example"
fi

# 2. Optionally set the Riot API key (arg #1 or the RIOT_API_KEY env var).
KEY="${1:-${RIOT_API_KEY:-}}"
if [ -n "$KEY" ]; then
  if grep -q '^RIOT_API_KEY=' .env; then
    sed -i.bak "s|^RIOT_API_KEY=.*|RIOT_API_KEY=${KEY}|" .env && rm -f .env.bak
  else
    echo "RIOT_API_KEY=${KEY}" >> .env
  fi
  echo "• Set RIOT_API_KEY in .env"
fi

# 3. Optionally set the initial tracked player (arg #2, e.g. "Name#EUNE").
if [ -n "${2:-}" ]; then
  if grep -q '^RIOT_INITIAL_PLAYER=' .env; then
    sed -i.bak "s|^RIOT_INITIAL_PLAYER=.*|RIOT_INITIAL_PLAYER=${2}|" .env && rm -f .env.bak
  else
    echo "RIOT_INITIAL_PLAYER=${2}" >> .env
  fi
  echo "• Set RIOT_INITIAL_PLAYER=${2} in .env"
fi

# 4. Refuse to start without a real key.
CURRENT_KEY="$(grep '^RIOT_API_KEY=' .env | cut -d= -f2- || true)"
if [ -z "$CURRENT_KEY" ] || [ "$CURRENT_KEY" = "your-dev-key-here" ]; then
  echo ""
  echo "✗ No Riot API key set."
  echo "  Get a dev key at https://developer.riotgames.com/ then run:"
  echo "      ./run.sh RGAPI-your-dev-key"
  exit 1
fi

# 5. Build and start the whole stack (Postgres + backend + frontend).
echo "• Building and starting containers…"
docker compose up --build -d

# 6. Discover the randomly-assigned host port and print the URL.
sleep 3
PORT="$(docker compose port frontend 80 2>/dev/null | sed 's/.*://' || true)"
echo ""
echo "✓ riot-analizer is up."
if [ -n "$PORT" ]; then
  echo "  Open:   http://localhost:${PORT}"
else
  echo "  Port:   docker compose port frontend 80   (then open http://localhost:<port>)"
fi
echo "  Logs:   docker compose logs -f"
echo "  Stop:   docker compose down"

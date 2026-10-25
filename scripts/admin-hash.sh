#!/usr/bin/env bash
#
# Print a BCrypt hash to use as ADMIN_PASSWORD_HASH.
#
# The password is typed at a hidden prompt and piped to the hasher over stdin —
# it is never echoed, saved, or passed as a command-line argument (so it doesn't
# end up in shell history or `ps`). Only the hash is printed (to stdout).
#
# Uses whichever is available:
#   - htpasswd          (built into macOS; Debian/Ubuntu: apt install apache2-utils)
#   - python3 + bcrypt  (pip install bcrypt)
#
# Usage:  ./scripts/admin-hash.sh      (or: make admin-hash)
#
set -euo pipefail

COST=12      # BCrypt work factor; the backend warns below 10
MIN_LEN=12   # refuse short passwords

read -r -s -p "Admin password: " pw; echo >&2
read -r -s -p "Repeat password: " pw2; echo >&2

if [ "$pw" != "$pw2" ]; then
  echo "Passwords don't match." >&2; exit 1
fi
if [ "${#pw}" -lt "$MIN_LEN" ]; then
  echo "Use at least $MIN_LEN characters (a long random passphrase is best)." >&2; exit 1
fi
if [ "$(printf '%s' "$pw" | wc -c)" -gt 72 ]; then
  echo "Note: BCrypt only uses the first 72 bytes of a password." >&2
fi

if command -v htpasswd >/dev/null 2>&1; then
  hash=$(printf '%s' "$pw" | htpasswd -niBC "$COST" admin | cut -d: -f2 | tr -d '\r\n')
elif command -v python3 >/dev/null 2>&1 && python3 -c 'import bcrypt' 2>/dev/null; then
  hash=$(printf '%s' "$pw" | COST="$COST" python3 -c \
    'import os, sys, bcrypt; print(bcrypt.hashpw(sys.stdin.buffer.read(), bcrypt.gensalt(int(os.environ["COST"]))).decode())')
else
  unset pw pw2
  echo "No BCrypt tool found. Install one of:" >&2
  echo "  - htpasswd  (macOS has it; Debian/Ubuntu: sudo apt install apache2-utils)" >&2
  echo "  - bcrypt for Python:  pip3 install bcrypt" >&2
  exit 1
fi
unset pw pw2

echo "$hash"
echo >&2
echo "Put this in .env (keep the single quotes):" >&2
echo "  ADMIN_PASSWORD_HASH='$hash'" >&2

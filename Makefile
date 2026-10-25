.PHONY: up down logs port rebuild admin-hash backend-test

# Build images and start everything in the background.
up:
	docker compose up --build -d

# Stop and remove containers (keeps the pgdata volume).
down:
	docker compose down

# Tail logs from all services.
logs:
	docker compose logs -f

# Print the random host port Docker assigned to the frontend.
port:
	docker compose port frontend 80

# Rebuild images from scratch and restart.
rebuild:
	docker compose up --build -d --force-recreate

# Prompt (twice, hidden) for an admin password and print its BCrypt hash.
# Nothing is stored; paste the hash into .env as ADMIN_PASSWORD_HASH='...'.
admin-hash:
	@./scripts/admin-hash.sh

# Run the backend test suite in a throwaway Maven container.
backend-test:
	docker run --rm -v "$(CURDIR)/backend":/w -w /w maven:3.9-eclipse-temurin-21 mvn -q -B test

# ---- Railway (cloud) ----
# One-shot, reproducible provision of the whole Railway environment from code.
# Requires: railway CLI logged in + linked, and RIOT_API_KEY exported.
.PHONY: railway-provision
railway-provision:
	./scripts/railway-provision.sh

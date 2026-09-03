.PHONY: up down logs port rebuild

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

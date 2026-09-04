# syntax=docker/dockerfile:1

# ---- Stage 1: build the static assets ----
FROM node:22-alpine AS build
WORKDIR /app

# Copy manifests first for dependency layer caching.
COPY package.json package-lock.json* ./
RUN npm ci || npm install

# Copy the rest of the frontend and build.
COPY . .
RUN npm run build

# ---- Stage 2: serve with nginx ----
FROM nginx:alpine

# The nginx image renders /etc/nginx/templates/*.template -> /etc/nginx/conf.d/
# at container start (via envsubst). Restrict substitution to our own vars so
# nginx's built-in $variables (e.g. $host, $uri) survive untouched.
ENV NGINX_ENVSUBST_FILTER="^(PORT|BACKEND_URL|BACKEND_HOST|NGINX_RESOLVER)$"

# Defaults let `docker compose` work with no extra config. On Railway these are
# overridden: PORT is auto-injected, BACKEND_URL/BACKEND_HOST point at the
# backend service's public domain.
ENV PORT=80
ENV BACKEND_URL=http://backend:8080
ENV BACKEND_HOST=backend
# Docker embedded DNS for local compose; Railway sets this to a public resolver.
ENV NGINX_RESOLVER=127.0.0.11

# Config template (SPA fallback + reverse proxy for /api and /actuator).
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

# Static build output.
COPY --from=build /app/dist /usr/share/nginx/html

# Documentation only; Railway routes to whatever port the app listens on ($PORT).
EXPOSE 80

# Inherit the base image's ENTRYPOINT (runs envsubst on templates) and its
# default CMD ["nginx", "-g", "daemon off;"].

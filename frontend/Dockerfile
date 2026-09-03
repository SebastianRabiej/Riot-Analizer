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

# Custom config: SPA fallback + reverse proxy for /api and /actuator.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Static build output.
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

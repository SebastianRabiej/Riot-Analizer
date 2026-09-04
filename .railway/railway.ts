/**
 * Railway Infrastructure as Code — the whole project in one file.
 * Docs: https://docs.railway.com/infrastructure-as-code
 *
 * Apply from the repo root (Railway CLI installed, `railway link`ed to a project):
 *   railway config plan      # SAFE dry-run diff — validate here first
 *   railway config apply     # create/update services to match this file
 *
 * This is the modern replacement for the per-service railway.json files (the
 * older "Config as Code", which Railway stops reading on 2026-12-01). If you
 * adopt this file, you can delete backend/railway.json and frontend/railway.json.
 *
 * DOMAINS: Railway IaC cannot create generated *.up.railway.app domains.
 * After the first `apply`, open each service in the dashboard ->
 * Settings -> Networking -> Generate Domain (backend on port 8080,
 * frontend on port 80), then run `railway config pull` to sync them
 * back into this file. The frontend BACKEND_URL below resolves once the
 * backend has its domain, so redeploy the frontend after generating it.
 *
 * NOTE: the IaC DSL is new; treat the service-shape bits marked (verify) below
 * as a draft and confirm the exact property names against `railway config plan`.
 * The cross-service wiring uses Railway ${{Service.VAR}} reference variables,
 * which resolve server-side at deploy time.
 */
import { defineRailway, github, postgres, project, service } from "railway/iac";

const REPO = "SebastianRabiej/Riot-Analizer";

export default defineRailway(() => {
  // ---- Managed Postgres ----
  // Named "Postgres" so the ${{Postgres.*}} references below resolve to it.
  const db = postgres("Postgres");

  // ---- Backend: Spring Boot, built from backend/Dockerfile ----
  const backend = service("backend", {
    source: github(REPO, { rootDirectory: "backend" }),
    builder: "dockerfile",              // (verify) build from backend/Dockerfile
    healthcheck: "/actuator/health",
    env: {
      // Pin the listening port so `railway domain -p 8080` is deterministic.
      PORT: "8080",
      // Spring wants a JDBC URL; compose it from Railway's Postgres variables.
      SPRING_DATASOURCE_URL:
        "jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}",
      SPRING_DATASOURCE_USERNAME: "${{Postgres.PGUSER}}",
      SPRING_DATASOURCE_PASSWORD: "${{Postgres.PGPASSWORD}}",
      // Secret kept OUT of git: supplied from the machine that runs `apply`.
      //   RIOT_API_KEY=xxx railway config apply
      RIOT_API_KEY: process.env.RIOT_API_KEY ?? "",
      // Optional tuning (defaults already in application.yml):
      // RIOT_INITIAL_PLAYER: "Name#EUNE",
      // LOG_LEVEL: "INFO",
    },
  });

  // ---- Frontend: nginx, built from frontend/Dockerfile ----
  const frontend = service("frontend", {
    source: github(REPO, { rootDirectory: "frontend" }),
    builder: "dockerfile",              // (verify) build from frontend/Dockerfile
    env: {
      // Pin nginx to :80 so `railway domain -p 80` is deterministic.
      PORT: "80",
      // Point the templated nginx at the backend's generated public domain.
      BACKEND_URL: "https://${{backend.RAILWAY_PUBLIC_DOMAIN}}",
      BACKEND_HOST: "${{backend.RAILWAY_PUBLIC_DOMAIN}}",
      // Public DNS resolver for the backend's public domain.
      NGINX_RESOLVER: "1.1.1.1",
    },
  });

  return project("riot-analizer", { resources: [db, backend, frontend] });
});

# syntax=docker/dockerfile:1

# ---- Stage 1: build the Spring Boot jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy the POM first so the dependency layer is cached and only re-resolved
# when pom.xml actually changes (not on every source edit).
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Now copy sources and build.
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- Stage 2: minimal runtime image ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user.
RUN groupadd --system spring && useradd --system --gid spring spring

# finalName in pom.xml is "app", so the artifact is target/app.jar
COPY --from=build /workspace/target/app.jar app.jar

RUN chown -R spring:spring /app
USER spring

EXPOSE 8080

# Healthcheck hits the Spring Boot Actuator health endpoint.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD ["sh", "-c", "wget -q -O - http://localhost:8080/actuator/health | grep -q '\"status\":\"UP\"' || exit 1"]

ENTRYPOINT ["java", "-jar", "app.jar"]

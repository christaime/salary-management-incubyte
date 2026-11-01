# ============================================================
# Stage 1: Build the single JAR (Gradle builds the frontend too)
# ============================================================
FROM gradle:8.5-jdk17 AS builder

# The build needs Node.js because build.gradle runs `npm` tasks.
# The gradle image is Debian-based; install Node from NodeSource.
USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
 && apt-get install -y --no-install-recommends nodejs \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# 1. Copy only the Gradle files first, so dependency resolution caches
#    even when source files change.
COPY backend/gradlew ./backend/gradlew
COPY backend/gradlew.bat ./backend/gradlew.bat
COPY backend/settings.gradle ./backend/settings.gradle
COPY backend/build.gradle ./backend/build.gradle
COPY backend/gradle/ ./backend/gradle/

# 2. Copy the frontend's package manifests so npmInstall can be cached.
COPY frontend/package.json ./frontend/package.json
COPY frontend/package-lock.json ./frontend/package-lock.json

# 3. Copy the full sources. The Gradle build expects ../frontend relative
#    to the backend project, so the directory layout in the image must
#    match the repo layout: /build/backend and /build/frontend.
COPY backend/src/ ./backend/src/
COPY frontend/ ./frontend/

# 4. Build the JAR. The build.gradle wires npmInstall → npmBuild →
#    copyFrontendToPublic → bootJar, so this single command produces the
#    bundle. Tests are skipped here (Render builds should be fast); CI
#    runs the full suite separately.
RUN cd backend && ./gradlew bootJar -x test --no-daemon

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine

# Non-root user for safety
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the built JAR
COPY --from=builder /build/backend/build/libs/*.jar app.jar

# The SQLite file lives at /app/data (matches DB_URL below)
RUN mkdir -p /app/data && chown -R appuser:appgroup /app

USER appuser

# Render injects PORT (default 10000). Spring reads SERVER_PORT, so we
# pass Render's PORT through.
ENV SERVER_PORT=${PORT:-8080}

# SQLite path is absolute, so the working directory doesn't matter and
# the file survives restarts inside /app/data (Render's persistent disk
# must be mounted there — see the Render setup section below).
ENV DB_URL=jdbc:sqlite:/app/data/salary-management.db

EXPOSE ${SERVER_PORT}

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
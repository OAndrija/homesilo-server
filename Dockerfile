# syntax=docker/dockerfile:1

# ── Build ─────────────────────────────────────────────────────────────────────
# Runs on the machine doing the build, whatever the target: the jar is the same everywhere,
# and building it under arm64 emulation would be slow.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/homesilo-server-*.jar /build/app.jar

# ── Runtime ───────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine
# Fonts: the slim JRE has none, and document thumbnails draw text (PDFs, Word, notes).
# pg_dump for the nightly backups, matching the PostgreSQL 17 the install files use.
RUN apk add --no-cache fontconfig ttf-dejavu postgresql17-client \
    && addgroup -S homesilo && adduser -S -G homesilo homesilo \
    && mkdir -p /data/storage /data/thumbnails /data/backups && chown -R homesilo:homesilo /data
WORKDIR /app
COPY --from=build /build/app.jar app.jar

USER homesilo
ENV STORAGE_BASE_PATH=/data/storage \
    THUMBNAIL_PATH=/data/thumbnails \
    BACKUP_PATH=/data/backups \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
VOLUME /data
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

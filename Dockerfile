# syntax=docker/dockerfile:1

# ── Build ─────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/homesilo-server-*.jar /build/app.jar

# ── Runtime ───────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S homesilo && adduser -S -G homesilo homesilo \
    && mkdir -p /data/storage && chown -R homesilo:homesilo /data
WORKDIR /app
COPY --from=build /build/app.jar app.jar

USER homesilo
ENV STORAGE_BASE_PATH=/data/storage \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
VOLUME /data
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

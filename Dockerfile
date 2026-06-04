# ─────────────────────────────────────────────────────────────
#  Multi-stage Dockerfile for Store Intelligence API
#  Stage 1 : Build with Gradle
#  Stage 2 : Minimal JRE runtime image
# ─────────────────────────────────────────────────────────────

# ── Stage 1: Build ───────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

# Copy Gradle wrapper and build files first (layer caching)
COPY gradle/          gradle/
COPY gradlew gradlew.bat gradle.properties ./
COPY build.gradle settings.gradle ./

# Download dependencies before copying source (layer cache)
RUN ./gradlew dependencies --no-daemon 2>/dev/null || true

# Copy full source and build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:25-jre

LABEL maintainer="Purplle Tech <purplletechchallenge2026@hackerearth.com>"
LABEL description="Store Intelligence API — real-time retail analytics"

WORKDIR /app

# Copy fat JAR from builder
COPY --from=builder /workspace/build/libs/*.jar app.jar

# Non-root user for security
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

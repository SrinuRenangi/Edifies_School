# ==============================================================================
# Multi-Stage Hardened Production Dockerfile
# Staff Attendance Management System
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Application
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM first for efficient layer caching
COPY pom.xml ./
COPY .mvn .mvn/
COPY mvnw ./
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B || true

# Copy source code and build artifact
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Minimal Hardened Runtime
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="Edifies School IT Security" \
      description="Hardened Staff Attendance Management System Runtime"

# Install curl/wget for health check
RUN apk --no-cache add curl tzdata

# Security: Create non-root dedicated service user and group (UID/GID 10001)
RUN addgroup -g 10001 -S attendance && \
    adduser -u 10001 -S attendance -G attendance -s /sbin/nologin

WORKDIR /app

# Prepare writable directories strictly for photos and temp
RUN mkdir -p /app/photos /app/temp && \
    chown -R attendance:attendance /app

# Copy executable jar from builder stage
COPY --from=builder --chown=attendance:attendance /build/target/staff-attendance-1.0.0.jar /app/app.jar

# Run as non-privileged service user
USER attendance:attendance

# Default environment configurations
ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Kolkata \
    PHOTO_STORAGE_ROOT=/app/photos \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# Health check to verify application readiness
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -f http://localhost:8080/ || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

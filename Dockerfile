# Multi-stage Docker build for GamerBell Spring Boot Application

# Stage 1: Build the application
FROM gradle:8.11-jdk21 AS builder

WORKDIR /app

# Copy all files needed for build
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

RUN ./gradlew bootJar -x test --no-daemon --parallel --build-cache

# Extract layers for optimized Docker image
RUN mkdir -p build/dependency && \
    cd build/dependency && \
    JAR_FILE=$(ls ../libs/*.jar | grep -v plain | head -1) && \
    java -Djarmode=layertools -jar $JAR_FILE extract

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-jammy

# Accept version as build argument
ARG VERSION=unknown

LABEL maintainer="mattlol85"
LABEL description="GamerBell - ESP32 Bell Button WebSocket Server"
LABEL version="${VERSION}"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.source="https://github.com/mattlol85/GamerBell"

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

WORKDIR /app

# Copy extracted layers from builder stage
COPY --from=builder /app/build/dependency/dependencies/ ./
COPY --from=builder /app/build/dependency/spring-boot-loader/ ./
COPY --from=builder /app/build/dependency/snapshot-dependencies/ ./
COPY --from=builder /app/build/dependency/application/ ./

# Create firmware directory with proper permissions
RUN mkdir -p /app/firmware && chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose application port
EXPOSE 8080

# Health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]


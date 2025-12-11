# Multi-stage Docker build for GamerBell Spring Boot Application

# Stage 1: Build the application
FROM gradle:8.11-jdk21 AS builder

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src src

# Build the application
RUN ./gradlew bootJar --no-daemon

# Extract layers for optimized Docker image
RUN mkdir -p build/dependency && \
    cd build/dependency && \
    java -Djarmode=layertools -jar ../libs/*.jar extract

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="mattlol85"
LABEL description="GamerBell - ESP32 Bell Button WebSocket Server"
LABEL version="0.4.0"

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


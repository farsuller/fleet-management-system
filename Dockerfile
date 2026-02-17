# Multi-stage build for optimized production image
# Stage 1: Build with Gradle and JDK 21
FROM gradle:8-jdk21 AS build

WORKDIR /app

# Copy project files
COPY . .

# Build Fat JAR (includes all dependencies)
RUN gradle buildFatJar --no-daemon

# Stage 2: Runtime with minimal JRE 21 Alpine
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/build/libs/*-all.jar app.jar

# Install wget for health checks
RUN apk add --no-cache wget

# Health check for Render platform
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8080}/health || exit 1

# Expose port (Render will inject PORT env var)
EXPOSE ${PORT:-8080}

# Run application as non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Start the application
CMD ["java", "-jar", "app.jar"]
